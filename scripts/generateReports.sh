#!/bin/bash

git -C $REPO_COVID_PATH pull
sbt package
$SPARK_HOME/bin/spark-submit --class "ReportsGenerator" --master local[2]  target/scala-2.11/spark-covid_2.11-1.0.jar