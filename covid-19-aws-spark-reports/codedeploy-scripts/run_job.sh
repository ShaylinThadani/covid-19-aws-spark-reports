cd /home/ec2-user/
mkdir -p out
rm out/*

REPO_COVID_PATH=/home/ec2-user/COVID-19
RESOURCES_PATH=/home/ec2-user/

git -C $REPO_COVID_PATH pull
RESOURCES_PATH=$RESOURCES_PATH REPO_COVID_PATH=$REPO_COVID_PATH /home/ec2-user/./spark-2.4.5-bin-hadoop2.7/bin/spark-submit --class "ReportsGenerator" --master local[1] covid-19-aws-spark-reports_2.11-1.0.jar
python create_index.py

## BUCKET=$(aws --region eu-central-1 ssm get-parameter --name covid-19-aws-spark-reports-bucket --query 'Parameter.Value' --output text)
aws s3 sync out s3://shaylin-covid-19-aws-spark-reports