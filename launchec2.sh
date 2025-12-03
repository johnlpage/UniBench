#!/bin/bash

REGION=eu-west-1
KEYNAME=johnpage_sa

# Get latest Amazon Linux 2023 AMI
AMI_ID=$(aws ec2 describe-images \
  --region $REGION \
  --owners amazon \
  --filters "Name=name,Values=al2023-ami-2023.*-x86_64" "Name=state,Values=available" \
  --query 'sort_by(Images, &CreationDate)[-1].ImageId' \
  --output text)

# Get security group ID
SG_ID=$(aws ec2 describe-security-groups \
  --region $REGION \
  --filters "Name=group-name,Values=johnpage-defaultgroup" \
  --query 'SecurityGroups[0].GroupId' \
  --output text)

# Tomorrow's date (macOS version)
EXPIRE_DATE=$(date -v+1d +%Y-%m-%d)

echo "Launching instance..."
echo "AMI: $AMI_ID"
echo "Security Group: $SG_ID"
echo "Expire Date: $EXPIRE_DATE"

# Launch instance
INSTANCE_ID=$(aws ec2 run-instances \
  --region $REGION \
  --image-id $AMI_ID \
  --instance-type c5.4xlarge \
  --key-name $KEYNAME \
  --security-group-ids $SG_ID \
  --tag-specifications \
    "ResourceType=instance,Tags=[
      {Key=Name,Value=JPage - Unibench client},
      {Key=owner,Value=john.page@mongodb.com},
      {Key=purpose,Value=other},
      {Key=expire-on,Value=$EXPIRE_DATE}
    ]" \
  --query 'Instances[0].InstanceId' \
  --output text)

echo "Instance ID: $INSTANCE_ID"
echo "Waiting for instance to start..."

# Wait for running state
aws ec2 wait instance-running --region $REGION --instance-ids $INSTANCE_ID

# Get public IP
PUBLIC_IP=$(aws ec2 describe-instances \
  --region $REGION \
  --instance-ids $INSTANCE_ID \
  --query 'Reservations[0].Instances[0].PublicIpAddress' \
  --output text)

echo ""
echo "✓ Instance launched successfully!"
echo "Instance ID: $INSTANCE_ID"
echo "Public IP: $PUBLIC_IP"
echo "Adding Access to MongoDB Cloud Project"
atlas accessLists create $PUBLIC_IP   --deleteAfter $(date -u -v+24H '+%Y-%m-%dT%H:%M:%SZ') --projectId $ATLAS_PROJECT_ID
echo "Adding IP address to API Key to allow cluster creation"
atlas projects apiKeys accessLists create $PUBLIC_IP  --id $ATLAS_PUBLIC_KEY  --projectId $ATLAS_PROJECT_ID

echo ""
echo "Connect with: ssh -i ~/.ssh/$KEYNAME.pem ec2-user@$PUBLIC_IP"

