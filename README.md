# Java + Tomcat + Docker + ECR + EKS + Jenkins (DEV/PROD)

A learning project that builds a small Java Servlet web app into a WAR, packages it in a Tomcat container, pushes the image to Amazon ECR, and deploys it to DEV and (after manual approval) PROD in EKS.

## Architecture and cost choices
- AWS region: `ap-south-1` (Mumbai)
- One EKS cluster, two namespaces: `dev` and `prod` (learning only; not strong production isolation)
- One private ECR repository
- Kubernetes `ClusterIP` Services; use `kubectl port-forward` for testing so we don't create an ALB/NLB yet
- Jenkins can run on a separate EC2 instance or on an existing trusted Jenkins host
- This repository intentionally does not create AWS infrastructure or use Terraform

## Application
`/` and `/health` return a small JSON response showing the application name and environment.

## Prerequisites
- Git repository containing this project
- AWS account and region `ap-south-1`
- ECR repository named `java-tomcat-practice`
- EKS cluster named `k8s-practice`
- `kubectl` configured for the cluster on the Jenkins agent
- Jenkins agent with Java 17, Maven 3.9+, Docker CLI/daemon access, AWS CLI v2, `kubectl`, and `envsubst`
- Jenkins Pipeline and Pipeline: Stage View plugins; credentials configured as described below
- Jenkins agent IAM identity can push to the ECR repository and deploy only to the intended EKS namespaces. Prefer an attached EC2 IAM role over static AWS keys.

## Build and test locally
```bash
mvn clean package
docker build -t java-tomcat-practice:local .
docker run --rm -p 8080:8080 -e APP_ENV=local java-tomcat-practice:local
curl http://localhost:8080/
curl http://localhost:8080/health
```

## AWS setup (manual)
1. Select `ap-south-1`.
2. Create a private ECR repository named `java-tomcat-practice`.
3. Create or use an EKS cluster named `k8s-practice`. For cost control, create it only while practicing and delete it when done. EKS control-plane fees and worker/network resources can cost money.
4. Configure the Jenkins agent's AWS identity. Verify with `aws sts get-caller-identity`.
5. Ensure that identity can authenticate to EKS and is authorized to deploy to `dev` and `prod`. AWS IAM permission to describe the cluster alone is not enough; Kubernetes authorization must also be configured (EKS access entry or the cluster's supported access mechanism). Avoid granting cluster-admin broadly.
6. Ensure the worker nodes can pull from ECR. Nodes in the same AWS account commonly use their node IAM role for ECR pulls; check node role permissions if pulls fail.

Example commands to create ECR repository and kubeconfig (run from an appropriately authorized admin shell):
```bash
aws ecr create-repository --repository-name java-tomcat-practice --region ap-south-1
aws eks update-kubeconfig --name k8s-practice --region ap-south-1
kubectl create namespace dev
kubectl create namespace prod
```
If a resource already exists, inspect it instead of blindly recreating it.

## Jenkins configuration
Create a Pipeline job pointing at this Git repository and use `Jenkinsfile` as the pipeline script.
Set these Jenkins environment variables (Manage Jenkins → System or job configuration):
- `AWS_REGION` = `ap-south-1`
- `ECR_REPOSITORY` = `java-tomcat-practice`
- `EKS_CLUSTER` = `k8s-practice`
- `AWS_ACCOUNT_ID` = your 12-digit AWS account ID

The Jenkins agent's AWS identity is used by `aws` CLI. Do not put AWS access keys in the Jenkinsfile or source repository.

## Pipeline behavior
1. Checkout source
2. Run Maven tests/package
3. Build Docker image tagged with the Git commit
4. Push that exact image tag to ECR
5. Deploy to `dev`
6. Wait for rollout
7. Require a human approval
8. Deploy the same image tag to `prod`
9. Wait for rollout

The pipeline deliberately promotes the same image from DEV to PROD instead of rebuilding it.

## Manual validation
```bash
kubectl get deploy,pods,svc -n dev
kubectl get deploy,pods,svc -n prod
kubectl logs deployment/java-tomcat-practice -n dev
kubectl port-forward service/java-tomcat-practice 8080:80 -n dev
# In another terminal:
curl http://localhost:8080/
curl http://localhost:8080/health
```

To test PROD, use a different local port:
```bash
kubectl port-forward service/java-tomcat-practice 8081:80 -n prod
curl http://localhost:8081/
```

## Cleanup
Delete app resources and namespaces:
```bash
kubectl delete namespace dev prod
```
Delete the ECR repository and its images if no longer needed:
```bash
aws ecr delete-repository --repository-name java-tomcat-practice --region ap-south-1 --force
```
If you created the EKS cluster only for this lab, delete it only after confirming nothing else depends on it:
```bash
eksctl delete cluster --name k8s-practice --region ap-south-1 --wait
```
Also inspect and remove any Jenkins EC2 instance, EBS volumes, load balancers, NAT Gateways, Elastic IPs, or other resources created for the lab. Deleting the cluster does not necessarily remove independently created resources. Don't delete shared resources.

## Troubleshooting
- `ImagePullBackOff`: check image URI/tag, node network connectivity, and node IAM permissions for ECR.
- `kubectl unauthorized` / `Forbidden`: check the Jenkins IAM identity's EKS access entry and Kubernetes RBAC permissions.
- `no basic auth credentials` on ECR push: rerun the ECR login command and check IAM ECR push permissions.
- `CrashLoopBackOff`: `kubectl logs deployment/java-tomcat-practice -n dev` and `kubectl describe pod ... -n dev`.
- `mvn` or Docker command missing: install tools on the Jenkins agent, not just on the administrator's laptop.
