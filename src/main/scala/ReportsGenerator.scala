import java.time.format.DateTimeFormatter

import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.sql.functions._


object ReportsGenerator {

  def main(args: Array[String]) {
    val covidDataPath = sys.env.get("REPO_COVID_PATH")
    if (covidDataPath.isEmpty) {
      println("REPO_COVID_PATH is missing")
      System.exit(1)
    }

    val spark = SparkSession.
      builder.appName("Simple Application").
      getOrCreate()
    import spark.implicits._

    val resourcesPathPrefix  = sys.env.get("RESOURCES_PATH").getOrElse("src/main/resources/")
    val europeanCountries = spark.
      read.
      option("header", true).
      option("inferSchema", true).
      csv(resourcesPathPrefix + "countries_europe.csv")

    val countriesPopulation = spark.
      read.
      option("header", true).
      option("inferSchema", true).
      csv(resourcesPathPrefix + "countries_population.csv")

    val pathPrefix =  "file:///"+ covidDataPath.get + "/csse_covid_19_data/csse_covid_19_daily_reports/"
    val data = spark.read.
      option("header", true).
      option("inferSchema", true).
      option("mode","FAILFAST"). // immediately fail if headers change again
      option("timestampFormat", "MM/dd/yy HH:mm"). // additional format in files
      csv(pathPrefix + "03-2[3-9]*", pathPrefix + "03-3[0-9]*", pathPrefix + "0[4-9]-*"). // import reports with newest headers only
      cache()


    val dataWithDay = data.
      select("Country_Region", "Confirmed", "Deaths", "Recovered", "Active", "Last_Update").
      withColumn("Day", to_date(col("Last_Update"))).cache()


    val winCountry = org.apache.spark.sql.expressions.Window.partitionBy("Country_Region").orderBy("Day")
    val preparedData = dataWithDay.
      groupBy("Country_Region", "Day").sum("Deaths", "Confirmed", "Recovered").
      withColumn("Daily_Confirmed_Rate", col("sum(Confirmed)") - lag("sum(Confirmed)", 1).over(winCountry)).
      withColumn("Daily_Confirmed_%", round((col("Daily_Confirmed_Rate") - lag("Daily_Confirmed_Rate", 1).over(winCountry)) / lag("Daily_Confirmed_Rate", 1).over(winCountry) * 100, 2)).
      withColumn("Daily_Deaths_Rate", col("sum(Deaths)") - lag("sum(Deaths)", 1).over(winCountry)).
      withColumn("Daily_Deaths_%", round((col("Daily_Deaths_Rate") - lag("Daily_Deaths_Rate", 1).over(winCountry)) / lag("Daily_Deaths_Rate", 1).over(winCountry) * 100, 2)).
      withColumn("Daily_Recovered_Rate", col("sum(Recovered)") - lag("sum(Recovered)", 1).over(winCountry)).cache().
      withColumn("Daily_Recovered_%", round((col("Daily_Recovered_Rate") - lag("Daily_Recovered_Rate", 1).over(winCountry)) / lag("Daily_Recovered_Rate", 1).over(winCountry) * 100, 2))


    // append population
    val dataWithPopulation = preparedData.join(countriesPopulation, preparedData("Country_Region") === countriesPopulation("Country")).
      withColumn("Infected_%", round(col("sum(Confirmed)") / col("Population") * 100, 2)).
      drop("Country").
      cache()

    (europeanCountries.select("country").collect().toSeq.map(r => r.get(0)) :+ "US").
      foreach(country =>
        reflect.io.File("out/" + country + ".html").writeAll(
          toHTMLString(dataWithPopulation.
            filter("Country_Region = '" + country + "'").
            sort($"Day".desc))
        )
      )

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val yesterday = java.time.LocalDate.now.minusDays(1).format(formatter)

    reflect.io.File("out/Global_Daily_Confirmed_Rate.html").writeAll(
      toHTMLString(dataWithPopulation.
        filter("Day = '" + yesterday + "'").
        sort($"Daily_Confirmed_Rate".desc))
    )

    val europeanCountriesWithPopulation = countriesPopulation.join(europeanCountries, "Country")
    val sumPopulation = europeanCountriesWithPopulation.groupBy().sum("Population").take(1)(0).getLong(0)

    // tmp col to apply window over frame
    val winPartitionID = org.apache.spark.sql.expressions.Window.partitionBy("PartitionID").orderBy("Day")
    val europeDaily = dataWithDay.
      join(europeanCountriesWithPopulation, dataWithDay("Country_Region") === europeanCountriesWithPopulation("Country")).
      groupBy("Day").sum("Deaths", "Confirmed", "Recovered").
      withColumn("PartitionID", lit("PartitionID")).
      withColumn("Daily_Confirmed_Rate", col("sum(Confirmed)") - lag("sum(Confirmed)", 1).over(winPartitionID)).
      withColumn("Daily_Confirmed_%", round((col("Daily_Confirmed_Rate") - lag("Daily_Confirmed_Rate", 1).over(winPartitionID)) / lag("Daily_Confirmed_Rate", 1).over(winPartitionID) * 100, 2)).
      withColumn("Daily_Deaths_Rate", col("sum(Deaths)") - lag("sum(Deaths)", 1).over(winPartitionID)).
      withColumn("Daily_Deaths_%", round((col("Daily_Deaths_Rate") - lag("Daily_Deaths_Rate", 1).over(winPartitionID)) / lag("Daily_Deaths_Rate", 1).over(winPartitionID) * 100, 2)).
      withColumn("Daily_Recovered_Rate", col("sum(Recovered)") - lag("sum(Recovered)", 1).over(winPartitionID)).cache().
      withColumn("Daily_Recovered_%", round((col("Daily_Recovered_Rate") - lag("Daily_Recovered_Rate", 1).over(winPartitionID)) / lag("Daily_Recovered_Rate", 1).over(winPartitionID) * 100, 2)).
      withColumn("sumPopulation", lit(sumPopulation)).
      withColumn("Infected_%", round(col("sum(Confirmed)") / col("sumPopulation") * 100, 2)).
      drop("PartitionID")

    reflect.io.File("out/Europe_Daily.html").writeAll(
      toHTMLString(europeDaily.sort($"Day".desc))
    )

    spark.stop()
  }

  def toHTMLString(dt: Dataset[_]): String = {
    val sb = new StringBuilder
    sb.append("<html>")

    sb.append("<head>").append("<style>").append(
      """  table {
                  font-family: arial, sans-serif;
                  border-collapse: collapse;
                  width: 100%;
                }

                td, th {
                  border: 1px solid #dddddd;
                  text-align: right;
                  padding: 8px;
                }

                tr:nth-child(even) {
                  background-color: #dddddd;
                }
                """).append("</style>").append("</head>")

    sb.append("<body>").append("<table>")

    sb.append("<tr>")
    dt.schema.fieldNames.toSeq.foreach(header => {
      sb.append("<th>").append(header).append("</th>")

    })
    sb.append("</tr>")

    dt.select("*").collect().toSeq.foreach(row => {
      sb.append("<tr>")
      (0 to row.size - 1).foreach(i => sb.append("<td>").append(row.get(i)).append("</td>"))
      sb.append("</tr>")
    }
    )
    sb.append("</table>").append("</body>").append("</html>")

    sb.toString()
  }
}




