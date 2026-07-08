data "aws_partition" "current" {}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_eks_cluster_auth" "this" {
  name = aws_eks_cluster.this.name
}

data "aws_vpc" "selected" {
  id = var.vpc_id
}

data "aws_iam_policy_document" "cluster_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "node_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "aws_load_balancer_controller_assume_role" {
  count = local.install_aws_load_balancer_controller_iam ? 1 : 0

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.this[0].arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(aws_iam_openid_connect_provider.this[0].url, "https://", "")}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(aws_iam_openid_connect_provider.this[0].url, "https://", "")}:sub"
      values   = ["system:serviceaccount:${var.aws_load_balancer_controller_namespace}:${var.aws_load_balancer_controller_service_account_name}"]
    }
  }
}

data "aws_iam_policy_document" "aws_load_balancer_controller" {
  count = local.install_aws_load_balancer_controller_iam ? 1 : 0

  statement {
    effect = "Allow"
    actions = [
      "iam:CreateServiceLinkedRole",
    ]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "iam:AWSServiceName"
      values   = ["elasticloadbalancing.amazonaws.com"]
    }
  }

  statement {
    effect = "Allow"
    actions = [
      "ec2:DescribeAccountAttributes",
      "ec2:DescribeAddresses",
      "ec2:DescribeAvailabilityZones",
      "ec2:DescribeInternetGateways",
      "ec2:DescribeVpcs",
      "ec2:DescribeVpcPeeringConnections",
      "ec2:DescribeSubnets",
      "ec2:DescribeSecurityGroups",
      "ec2:DescribeInstances",
      "ec2:DescribeNetworkInterfaces",
      "ec2:DescribeTags",
      "ec2:GetCoipPoolUsage",
      "ec2:DescribeCoipPools",
      "elasticloadbalancing:DescribeLoadBalancers",
      "elasticloadbalancing:DescribeLoadBalancerAttributes",
      "elasticloadbalancing:DescribeListeners",
      "elasticloadbalancing:DescribeListenerCertificates",
      "elasticloadbalancing:DescribeSSLPolicies",
      "elasticloadbalancing:DescribeRules",
      "elasticloadbalancing:DescribeTargetGroups",
      "elasticloadbalancing:DescribeTargetGroupAttributes",
      "elasticloadbalancing:DescribeTargetHealth",
      "elasticloadbalancing:DescribeTags",
      "elasticloadbalancing:DescribeTrustStores",
      "elasticloadbalancing:DescribeListenerAttributes",
      "elasticloadbalancing:DescribeCapacityReservation",
    ]
    resources = ["*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "cognito-idp:DescribeUserPoolClient",
      "acm:ListCertificates",
      "acm:DescribeCertificate",
      "iam:ListServerCertificates",
      "iam:GetServerCertificate",
      "waf-regional:GetWebACL",
      "waf-regional:GetWebACLForResource",
      "waf-regional:AssociateWebACL",
      "waf-regional:DisassociateWebACL",
      "wafv2:GetWebACL",
      "wafv2:GetWebACLForResource",
      "wafv2:AssociateWebACL",
      "wafv2:DisassociateWebACL",
      "shield:GetSubscriptionState",
      "shield:DescribeProtection",
      "shield:CreateProtection",
      "shield:DeleteProtection",
      "ec2:AuthorizeSecurityGroupIngress",
      "ec2:RevokeSecurityGroupIngress",
    ]
    resources = ["*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "ec2:CreateSecurityGroup",
    ]
    resources = ["*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "ec2:CreateTags",
    ]
    resources = ["arn:${data.aws_partition.current.partition}:ec2:*:*:security-group/*"]

    condition {
      test     = "StringEquals"
      variable = "ec2:CreateAction"
      values   = ["CreateSecurityGroup"]
    }

    condition {
      test     = "Null"
      variable = "aws:RequestTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }

  statement {
    effect = "Allow"
    actions = [
      "ec2:CreateTags",
      "ec2:DeleteTags",
    ]
    resources = ["arn:${data.aws_partition.current.partition}:ec2:*:*:security-group/*"]

    condition {
      test     = "Null"
      variable = "aws:RequestTag/elbv2.k8s.aws/cluster"
      values   = ["true"]
    }

    condition {
      test     = "Null"
      variable = "aws:ResourceTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }

  statement {
    effect = "Allow"
    actions = [
      "ec2:AuthorizeSecurityGroupIngress",
      "ec2:RevokeSecurityGroupIngress",
      "ec2:DeleteSecurityGroup",
    ]
    resources = ["*"]

    condition {
      test     = "Null"
      variable = "aws:ResourceTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }

  statement {
    effect = "Allow"
    actions = [
      "elasticloadbalancing:CreateLoadBalancer",
      "elasticloadbalancing:CreateTargetGroup",
    ]
    resources = ["*"]

    condition {
      test     = "Null"
      variable = "aws:RequestTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }

  statement {
    effect = "Allow"
    actions = [
      "elasticloadbalancing:CreateListener",
      "elasticloadbalancing:DeleteListener",
      "elasticloadbalancing:CreateRule",
      "elasticloadbalancing:DeleteRule",
    ]
    resources = ["*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "elasticloadbalancing:AddTags",
      "elasticloadbalancing:RemoveTags",
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:elasticloadbalancing:*:*:targetgroup/*/*",
      "arn:${data.aws_partition.current.partition}:elasticloadbalancing:*:*:loadbalancer/net/*/*",
      "arn:${data.aws_partition.current.partition}:elasticloadbalancing:*:*:loadbalancer/app/*/*",
    ]

    condition {
      test     = "Null"
      variable = "aws:RequestTag/elbv2.k8s.aws/cluster"
      values   = ["true"]
    }

    condition {
      test     = "Null"
      variable = "aws:ResourceTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }

  statement {
    effect = "Allow"
    actions = [
      "elasticloadbalancing:AddTags",
      "elasticloadbalancing:RemoveTags",
    ]
    resources = [
      "arn:${data.aws_partition.current.partition}:elasticloadbalancing:*:*:listener/net/*/*/*",
      "arn:${data.aws_partition.current.partition}:elasticloadbalancing:*:*:listener/app/*/*/*",
      "arn:${data.aws_partition.current.partition}:elasticloadbalancing:*:*:listener-rule/net/*/*/*",
      "arn:${data.aws_partition.current.partition}:elasticloadbalancing:*:*:listener-rule/app/*/*/*",
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "elasticloadbalancing:ModifyLoadBalancerAttributes",
      "elasticloadbalancing:SetIpAddressType",
      "elasticloadbalancing:SetSecurityGroups",
      "elasticloadbalancing:SetSubnets",
      "elasticloadbalancing:DeleteLoadBalancer",
      "elasticloadbalancing:ModifyTargetGroup",
      "elasticloadbalancing:ModifyTargetGroupAttributes",
      "elasticloadbalancing:DeleteTargetGroup",
      "elasticloadbalancing:ModifyListenerAttributes",
      "elasticloadbalancing:ModifyCapacityReservation",
    ]
    resources = ["*"]

    condition {
      test     = "Null"
      variable = "aws:ResourceTag/elbv2.k8s.aws/cluster"
      values   = ["false"]
    }
  }

  statement {
    effect = "Allow"
    actions = [
      "elasticloadbalancing:RegisterTargets",
      "elasticloadbalancing:DeregisterTargets",
    ]
    resources = ["arn:${data.aws_partition.current.partition}:elasticloadbalancing:*:*:targetgroup/*/*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "elasticloadbalancing:SetWebAcl",
      "elasticloadbalancing:ModifyListener",
      "elasticloadbalancing:AddListenerCertificates",
      "elasticloadbalancing:RemoveListenerCertificates",
      "elasticloadbalancing:ModifyRule",
    ]
    resources = ["*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "ec2:DescribeSecurityGroups",
      "ec2:DescribeSubnets",
      "ec2:DescribeVpcs",
      "ec2:DescribeAvailabilityZones",
      "ec2:DescribeInstances",
      "ec2:DescribeNetworkInterfaces",
      "ec2:DescribeTags",
      "ec2:GetSecurityGroupsForVpc",
      "ec2:DescribeIpamPools",
      "ec2:AllocateIpamPoolCidr",
      "ec2:ReleaseIpamPoolAllocation",
    ]
    resources = ["*"]
  }
}

locals {
  tags                                      = merge({ Name = var.cluster_name }, var.tags)
  addon_names                               = var.enable_cluster_addons ? toset([for addon in var.cluster_addon_names : addon if lower(addon) != "coredns"]) : toset([])
  node_all_tags                             = merge(local.tags, var.node_group_tags)
  use_node_launch_template                  = var.node_ami_image_id != null || var.node_volume_kms_key_arn != null
  effective_node_ssh_key_name               = var.node_ssh_key_name != null ? var.node_ssh_key_name : (length(aws_key_pair.node_ssh) > 0 ? aws_key_pair.node_ssh[0].key_name : null)
  effective_encryption_key_arn              = coalesce(var.cluster_encryption_key_arn, try(aws_kms_key.cluster_encryption[0].arn, null))
  effective_cluster_sg_ingress_cidr_ipv4    = coalesce(var.cluster_security_group_ingress_cidr_ipv4, data.aws_vpc.selected.cidr_block)
  sanitized_cluster_name                    = trim(replace(replace(lower(var.cluster_name), "/[^a-z0-9-]/", "-"), "/-+/", "-"), "-")
  bucket_name                               = coalesce(var.bucket_name, "${local.sanitized_cluster_name}-bucket")
  create_cluster_role                       = var.cluster_iam_role_arn == null
  effective_cluster_role_arn                = var.cluster_iam_role_arn != null ? var.cluster_iam_role_arn : aws_iam_role.cluster[0].arn
  create_node_role                          = var.node_iam_role_arn == null
  effective_node_role_arn                   = var.node_iam_role_arn != null ? var.node_iam_role_arn : aws_iam_role.node[0].arn
  install_aws_load_balancer_controller_iam  = var.enable_aws_load_balancer_controller && var.enable_irsa
  install_aws_load_balancer_controller_helm = var.enable_aws_load_balancer_controller_helm && local.install_aws_load_balancer_controller_iam
}

resource "aws_key_pair" "node_ssh" {
  count = var.node_ssh_public_key != null ? 1 : 0

  key_name_prefix = "${var.cluster_name}-${var.node_group_name}-"
  public_key      = trimspace(var.node_ssh_public_key)
  tags            = local.node_all_tags
}

provider "helm" {
  kubernetes = {
    host                   = aws_eks_cluster.this.endpoint
    cluster_ca_certificate = base64decode(aws_eks_cluster.this.certificate_authority[0].data)
    token                  = data.aws_eks_cluster_auth.this.token
  }
}

resource "aws_s3_bucket" "platform_files" {
  count = var.create_bucket ? 1 : 0

  bucket        = local.bucket_name
  force_destroy = var.bucket_force_destroy
  tags          = merge(local.tags, { Name = local.bucket_name })
}

resource "aws_s3_bucket_versioning" "platform_files" {
  count = var.create_bucket ? 1 : 0

  bucket = aws_s3_bucket.platform_files[0].id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "platform_files" {
  count = var.create_bucket ? 1 : 0

  bucket = aws_s3_bucket.platform_files[0].id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "platform_files" {
  count = var.create_bucket ? 1 : 0

  bucket = aws_s3_bucket.platform_files[0].id

  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = var.bucket_kms_key_arn
      sse_algorithm     = var.bucket_kms_key_arn != null ? "aws:kms" : "AES256"
    }
  }
}

resource "aws_iam_role" "cluster" {
  count = local.create_cluster_role ? 1 : 0

  name               = "${var.cluster_name}-cluster-role"
  assume_role_policy = data.aws_iam_policy_document.cluster_assume_role.json
  tags               = local.tags
}

resource "aws_iam_role_policy_attachment" "cluster" {
  count = local.create_cluster_role ? 1 : 0

  role       = aws_iam_role.cluster[0].name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_iam_role_policy_attachment" "cluster_vpc_resource_controller" {
  count = local.create_cluster_role ? 1 : 0

  role       = aws_iam_role.cluster[0].name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKSVPCResourceController"
}

resource "aws_iam_role" "node" {
  count = local.create_node_role ? 1 : 0

  name               = "${var.cluster_name}-node-role"
  assume_role_policy = data.aws_iam_policy_document.node_assume_role.json
  tags               = local.tags
}

resource "aws_iam_role_policy_attachment" "node_worker" {
  count = local.create_node_role ? 1 : 0

  role       = aws_iam_role.node[0].name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "node_cni" {
  count = local.create_node_role ? 1 : 0

  role       = aws_iam_role.node[0].name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "node_ecr_readonly" {
  count = local.create_node_role ? 1 : 0

  role       = aws_iam_role.node[0].name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy_attachment" "node_ssm" {
  count = (local.create_node_role && var.attach_ssm_policy_to_nodes) ? 1 : 0

  role       = aws_iam_role.node[0].name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "cluster_kms_key" {
  count = var.create_cluster_encryption_key ? 1 : 0

  statement {
    sid    = "AllowAccountAdministration"
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = ["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"]
    }

    actions   = ["kms:*"]
    resources = ["*"]
  }

  statement {
    sid    = "AllowEKSClusterRoleUsage"
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = [local.effective_cluster_role_arn]
    }

    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
      "kms:Encrypt",
      "kms:GenerateDataKey*",
      "kms:ReEncrypt*",
    ]
    resources = ["*"]
  }
}

resource "aws_kms_key" "cluster_encryption" {
  count = var.create_cluster_encryption_key ? 1 : 0

  description         = "KMS key for EKS secrets encryption for ${var.cluster_name}"
  enable_key_rotation = true
  policy              = data.aws_iam_policy_document.cluster_kms_key[0].json
  tags                = local.tags
}

resource "aws_kms_alias" "cluster_encryption" {
  count = var.create_cluster_encryption_key ? 1 : 0

  name          = "alias/${var.cluster_name}-eks-secrets"
  target_key_id = aws_kms_key.cluster_encryption[0].key_id
}

resource "aws_eks_cluster" "this" {
  name     = var.cluster_name
  role_arn = local.effective_cluster_role_arn
  version  = var.kubernetes_version
  tags     = local.tags

  enabled_cluster_log_types = var.cluster_log_types

  access_config {
    authentication_mode                         = "API_AND_CONFIG_MAP"
    bootstrap_cluster_creator_admin_permissions = false
  }

  vpc_config {
    subnet_ids              = var.subnet_ids
    endpoint_private_access = var.cluster_endpoint_private_access
    endpoint_public_access  = var.cluster_endpoint_public_access
    public_access_cidrs     = var.cluster_endpoint_public_access ? var.cluster_endpoint_public_access_cidrs : null
  }

  dynamic "encryption_config" {
    for_each = local.effective_encryption_key_arn == null ? [] : [local.effective_encryption_key_arn]

    content {
      resources = ["secrets"]

      provider {
        key_arn = encryption_config.value
      }
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.cluster,
    aws_iam_role_policy_attachment.cluster_vpc_resource_controller,
    aws_kms_alias.cluster_encryption,
  ]
}

resource "aws_vpc_security_group_ingress_rule" "cluster_api_from_cidr" {
  count = var.manage_cluster_security_group_ingress_rule ? 1 : 0

  security_group_id = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
  description       = "Allow TCP ${var.cluster_security_group_ingress_port} from configured CIDR"
  cidr_ipv4         = local.effective_cluster_sg_ingress_cidr_ipv4
  from_port         = var.cluster_security_group_ingress_port
  ip_protocol       = "tcp"
  to_port           = var.cluster_security_group_ingress_port
}

resource "aws_eks_addon" "managed" {
  for_each = local.addon_names

  cluster_name                = aws_eks_cluster.this.name
  addon_name                  = each.value
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "OVERWRITE"
  tags                        = local.tags
}

resource "aws_launch_template" "node_custom_ami" {
  count = local.use_node_launch_template ? 1 : 0

  name_prefix = "${var.cluster_name}-${var.node_group_name}-"
  image_id    = var.node_ami_image_id
  key_name    = local.effective_node_ssh_key_name

  dynamic "block_device_mappings" {
    for_each = var.node_volume_kms_key_arn == null ? [] : [1]

    content {
      device_name = "/dev/xvda"

      ebs {
        encrypted   = true
        kms_key_id  = var.node_volume_kms_key_arn
        volume_size = var.node_disk_size
        volume_type = "gp3"
      }
    }
  }

  tag_specifications {
    resource_type = "instance"
    tags          = local.node_all_tags
  }

  tags = local.node_all_tags
}

resource "aws_eks_node_group" "this" {
  cluster_name         = aws_eks_cluster.this.name
  node_group_name      = var.node_group_name
  node_role_arn        = local.effective_node_role_arn
  subnet_ids           = var.subnet_ids
  capacity_type        = var.node_capacity_type
  disk_size            = local.use_node_launch_template ? null : var.node_disk_size
  instance_types       = var.node_instance_types
  ami_type             = local.use_node_launch_template ? null : var.node_ami_type
  force_update_version = true
  tags                 = local.node_all_tags

  dynamic "launch_template" {
    for_each = local.use_node_launch_template ? [aws_launch_template.node_custom_ami[0]] : []

    content {
      id      = launch_template.value.id
      version = tostring(launch_template.value.latest_version)
    }
  }

  dynamic "remote_access" {
    for_each = (!local.use_node_launch_template && local.effective_node_ssh_key_name != null) ? [1] : []

    content {
      ec2_ssh_key               = local.effective_node_ssh_key_name
      source_security_group_ids = length(var.node_ssh_source_security_group_ids) > 0 ? var.node_ssh_source_security_group_ids : null
    }
  }

  scaling_config {
    desired_size = var.node_desired_size
    min_size     = var.node_min_size
    max_size     = var.node_max_size
  }

  update_config {
    max_unavailable = 1
  }

  labels = var.node_labels

  dynamic "taint" {
    for_each = var.node_taints

    content {
      key    = taint.value.key
      value  = taint.value.value
      effect = taint.value.effect
    }
  }

  depends_on = [
    aws_key_pair.node_ssh,
    aws_iam_role_policy_attachment.node_worker,
    aws_iam_role_policy_attachment.node_cni,
    aws_iam_role_policy_attachment.node_ecr_readonly,
    aws_iam_role_policy_attachment.node_ssm,
    aws_eks_cluster.this,
    aws_eks_addon.managed,
  ]
}

resource "aws_eks_access_entry" "admins" {
  for_each = toset(var.cluster_admin_principal_arns)

  cluster_name  = aws_eks_cluster.this.name
  principal_arn = each.value
  type          = "STANDARD"
}

resource "aws_eks_access_policy_association" "admins" {
  for_each = toset(var.cluster_admin_principal_arns)

  cluster_name  = aws_eks_cluster.this.name
  principal_arn = each.value
  policy_arn    = "arn:${data.aws_partition.current.partition}:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [aws_eks_access_entry.admins]
}

data "tls_certificate" "oidc" {
  count = var.enable_irsa ? 1 : 0
  url   = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "this" {
  count = var.enable_irsa ? 1 : 0

  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.oidc[0].certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer
  tags            = local.tags
}

resource "aws_iam_role" "aws_load_balancer_controller" {
  count = local.install_aws_load_balancer_controller_iam ? 1 : 0

  name               = "${var.cluster_name}-aws-load-balancer-controller"
  assume_role_policy = data.aws_iam_policy_document.aws_load_balancer_controller_assume_role[0].json
  tags               = local.tags
}

resource "aws_iam_policy" "aws_load_balancer_controller" {
  count = local.install_aws_load_balancer_controller_iam ? 1 : 0

  name   = "${var.cluster_name}-AWSLoadBalancerController"
  policy = data.aws_iam_policy_document.aws_load_balancer_controller[0].json
  tags   = local.tags
}

resource "aws_iam_role_policy_attachment" "aws_load_balancer_controller" {
  count = local.install_aws_load_balancer_controller_iam ? 1 : 0

  role       = aws_iam_role.aws_load_balancer_controller[0].name
  policy_arn = aws_iam_policy.aws_load_balancer_controller[0].arn
}

resource "helm_release" "aws_load_balancer_controller" {
  count = local.install_aws_load_balancer_controller_helm ? 1 : 0

  name             = "aws-load-balancer-controller"
  namespace        = var.aws_load_balancer_controller_namespace
  repository       = "https://aws.github.io/eks-charts"
  chart            = "aws-load-balancer-controller"
  version          = var.aws_load_balancer_controller_chart_version
  create_namespace = false

  set = [
    {
      name  = "clusterName"
      value = aws_eks_cluster.this.name
    },
    {
      name  = "serviceAccount.create"
      value = "true"
    },
    {
      name  = "serviceAccount.name"
      value = var.aws_load_balancer_controller_service_account_name
    },
    {
      name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
      value = aws_iam_role.aws_load_balancer_controller[0].arn
    },
    {
      name  = "region"
      value = data.aws_region.current.region
    },
    {
      name  = "vpcId"
      value = data.aws_vpc.selected.id
    },
  ]

  depends_on = [
    aws_eks_node_group.this,
    aws_iam_role_policy_attachment.aws_load_balancer_controller,
  ]
}