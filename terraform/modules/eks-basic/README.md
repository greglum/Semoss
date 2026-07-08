# eks-basic Terraform module

This module creates a basic Amazon EKS cluster on top of an existing VPC.

It includes:

- An EKS control plane and a managed node group
- Core EKS add-ons (excluding CoreDNS)
- IAM roles and policy attachments for the cluster and nodes (with options to use existing roles)
- Optional EKS access entries for cluster admins
- Optional IRSA/OIDC support for Kubernetes service accounts
- Optional AWS Load Balancer Controller installation with a module-managed IRSA role
- Optional secrets encryption with a customer-managed KMS key

## Assumptions

- You already have a VPC and a set of subnets ready for EKS.
- You want a reusable baseline rather than a fully opinionated platform module.
- If you enable public access, you should narrow the allowed CIDRs.

## Preflight Checklist

Before you apply this module, make sure you have:

- An AWS account, region, and authenticated Terraform session
- An existing VPC and at least two subnets for the cluster and node group
- IAM permissions to create and manage EKS, IAM, EC2, and OIDC resources
- A decision on cluster endpoint access: private, public, or both
- Optional admin principal ARNs if you want EKS access entries created
- Optional KMS key ARN if you want Kubernetes secrets encrypted at rest
- Optional existing cluster IAM role ARN if you want to use a pre-created role
- Optional existing node IAM role ARN if you want to use a pre-created role
- Optional decision on whether worker nodes should have SSM access
- Optional custom node AMI ID if you want worker nodes to use a specific image
- Optional KMS key ARN if you want worker node EBS root volumes encrypted with a specific key
- Optional SSH key setup for node access (existing key name or new public key)
- Subnet tags compatible with AWS Load Balancer Controller if you enable it
- Terraform v1.5+ and AWS provider v5+ in the consuming repo

## Example

```hcl
module "eks" {
  source = "../../modules/eks-basic"

  cluster_name                 = "semoss-dev"
  kubernetes_version           = "1.31"
  vpc_id                       = var.vpc_id
  subnet_ids                   = var.subnet_ids
  cluster_admin_principal_arns = var.cluster_admin_principal_arns

  cluster_endpoint_private_access = true
  cluster_endpoint_public_access   = false
  cluster_security_group_ingress_port      = 8443
  cluster_security_group_ingress_cidr_ipv4 = "172.31.0.0/16"

  # SSH access options for worker nodes:
  # Use an existing EC2 key pair name
  node_ssh_key_name = "my-existing-key"

  # Or create a new EC2 key pair from a public key (do not set both)
  # node_ssh_public_key = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ..."

  # Optionally limit who can SSH to nodes when remote_access is used
  # node_ssh_source_security_group_ids = ["sg-0123456789abcdef0"]

  enable_aws_load_balancer_controller = true

  node_instance_types = ["m6i.large"]
  node_desired_size   = 2
  node_min_size       = 2
  node_max_size       = 4

  tags = {
    application = "semoss"
    environment = "dev"
  }
}
```

## Using Existing IAM Roles

If you already have cluster and/or node IAM roles, you can provide them to avoid duplication:

```hcl
module "eks" {
  source = "../../modules/eks-basic"

  cluster_name                = "semoss-prod"
  kubernetes_version          = "1.31"
  vpc_id                      = var.vpc_id
  subnet_ids                  = var.subnet_ids
  
  # Use existing cluster IAM role
  cluster_iam_role_arn = aws_iam_role.existing_cluster_role.arn
  
  # Use existing node IAM role
  node_iam_role_arn = aws_iam_role.existing_node_role.arn

  node_instance_types = ["m6i.large"]
  node_desired_size   = 2
  node_min_size       = 2
  node_max_size       = 4

  tags = {
    application = "semoss"
    environment = "prod"
  }
}
```

**Note:** When providing existing IAM roles, they must already have the required policies attached:
- **Cluster role** must have: `AmazonEKSClusterPolicy` and `AmazonEKSVPCResourceController`
- **Node role** must have: `AmazonEKSWorkerNodePolicy`, `AmazonEKS_CNI_Policy`, and `AmazonEC2ContainerRegistryReadOnly`

## Using a Custom Node AMI

Set `node_ami_image_id` to have the node group use your AMI through a launch template:

```hcl
module "eks" {
  source = "../../modules/eks-basic"

  cluster_name       = "semoss-dev"
  kubernetes_version = "1.31"
  vpc_id             = var.vpc_id
  subnet_ids         = var.subnet_ids

  node_ami_image_id  = "ami-0123456789abcdef0"
  node_instance_types = ["m6i.large"]
  node_desired_size   = 2
  node_min_size       = 2
  node_max_size       = 4
}
```

When `node_ami_image_id` is set, the module ignores `node_ami_type`.

## Encrypt Node Volumes With a Specific KMS Key

Set `node_volume_kms_key_arn` to use a customer-managed KMS key for worker node root EBS volume encryption:

```hcl
module "eks" {
  source = "../../modules/eks-basic"

  cluster_name             = "semoss-dev"
  kubernetes_version       = "1.31"
  vpc_id                   = var.vpc_id
  subnet_ids               = var.subnet_ids
  node_volume_kms_key_arn  = "arn:aws:kms:us-east-1:123456789012:key/11111111-2222-3333-4444-555555555555"

  node_instance_types = ["m6i.large"]
  node_desired_size   = 2
  node_min_size       = 2
  node_max_size       = 4
}
```

## AWS Load Balancer Controller

Set `enable_aws_load_balancer_controller = true` to have the module:

- Create an IAM role for service accounts scoped to the controller service account
- Attach the controller IAM policy
- Install the `aws-load-balancer-controller` Helm chart into `kube-system`

Related inputs:

- `enable_aws_load_balancer_controller`
- `aws_load_balancer_controller_namespace`
- `aws_load_balancer_controller_service_account_name`
- `aws_load_balancer_controller_chart_version`

Before enabling it, tag your subnets so the controller can discover where to place load balancers:

- Public ALBs: `kubernetes.io/role/elb = 1`
- Internal ALBs: `kubernetes.io/role/internal-elb = 1`
- Cluster discovery: `kubernetes.io/cluster/<cluster-name> = shared` or `owned`

## Inputs

Security group access behavior in this module:

- The module adds one ingress rule on the EKS-created cluster security group.
- The rule allows TCP traffic on `cluster_security_group_ingress_port` from `cluster_security_group_ingress_cidr_ipv4`.
- If `cluster_security_group_ingress_cidr_ipv4` is null, the module defaults to the selected VPC CIDR block.

SSH key behavior for worker nodes:

- Set `node_ssh_key_name` to use an existing EC2 key pair.
- Or set `node_ssh_public_key` to have the module create an EC2 key pair automatically.
- If `node_ami_image_id` is null, the module configures node SSH via the EKS managed node group `remote_access` block.
- If `node_ami_image_id` is set, the module applies the SSH key through the launch template.
- Optionally set `node_ssh_source_security_group_ids` to limit inbound SSH sources for managed node group remote access.

## CoreDNS Add-on Ordering

To avoid add-on and node-group dependency cycles, this module does not manage the `coredns` add-on.
Create CoreDNS in your downstream stack after node group creation.

See `variables.tf` for the full list of inputs.

## Outputs

See `outputs.tf` for the full list of outputs.