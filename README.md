# AWS Apache Spark COVID-19 Reports

A side project that generates COVID-19 reports from
[Johns Hopkins COVID-19 (2019-nCoV) Data Repository](https://github.com/CSSEGISandData/COVID-19).

This project creates and displays reports that provide a detailed analysis on Covid-19 statistics for various regions and countries including the:

- date
- deaths
- recovered
- daily confirmed rate
- daily death rate
- daily death percentage
- daily recovered rate
- daily recovered percentage
- sum population
- infected percentage

## AWS CI/CD

The project make use of [AWS CodePipeline](https://aws.amazon.com/codepipeline/).
The pipeline:

1. builds the project from code through [AWS CodeBuild](https://aws.amazon.com/codebuild/)
2. deploys the project to an EC2 Instance through [AWS CodeDeploy](https://aws.amazon.com/codedeploy/)
3. runs the spark job in standalone/local mode on the [EC2 Instance](https://aws.amazon.com/ec2/)
4. and uploads the generated reports into an [AWS S3 Bucket](https://aws.amazon.com/s3/)

The CI/CD service is being modelled and provisioned with [AWS CloudFormation](https://aws.amazon.com/cloudformation/)
