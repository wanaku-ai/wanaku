package ai.wanaku.mcp.servers.aws.security;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import ai.wanaku.core.forward.discovery.client.AutoDiscoveryClient;
import ai.wanaku.core.forward.discovery.client.ForwardRegistrationManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import io.quarkus.qute.Qute;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

public class S3Tool {
    @Inject
    Instance<ForwardRegistrationManager> registrationManager;

    @Tool(description = "Get Bucket list for a region.")
    String getBuckets(@ToolArg(description = "AWS Region") String region) {
        S3Client s3Client = S3Client.builder().credentialsProvider(DefaultCredentialsProvider.builder().build()).region(Region.of(region)).build();
        if (region != null) {
            return formatBucket(s3Client.listBuckets(ListBucketsRequest.builder().bucketRegion(region).build()));
        } else {
            return formatBucket(s3Client.listBuckets(ListBucketsRequest.builder().build()));
        }
    }

    @Tool(description = "Check Safety of a Bucket")
    String safeBucket(@ToolArg(description = "Bucket Name") String bucketName) {
        S3Client s3Client = S3Client.builder().credentialsProvider(DefaultCredentialsProvider.builder().build()).build();
        return formatBucketSecurity(s3Client, bucketName);
    }

    String formatBucket(ListBucketsResponse bucketsResponse) {


        return bucketsResponse.buckets().stream().map(bucket -> {
                return Qute.fmt(
                        """
                                Bucket: {p.name},
                                Region: {p.bucketRegion},
                                """,
                        Map.of("p", bucket));
            }).collect(Collectors.joining("\n---\n"));
    }

    String formatBucketSecurity(S3Client s3Client, String bucketName) {
                return new String(
                        "Block Public Access: " + checkBlockPublicAccess(s3Client, bucketName) + System.lineSeparator()
                                + "Safe Bucket Policy: " + checkBucketPolicy(s3Client, bucketName) + System.lineSeparator()
                                + "Safe Bucket ACL: " + checkBucketAcl(s3Client, bucketName) + System.lineSeparator()
                                + "Encryption Enabled: " + checkEncryption(s3Client,bucketName) + System.lineSeparator()
                                + "Versioning Enabled: " + checkVersioning(s3Client,bucketName) + System.lineSeparator()
                                + "Logging Enabled: " + checkLogging(s3Client,bucketName) + System.lineSeparator()
                                + "MFA Delete Enabled: " + checkMfaDelete(s3Client, bucketName) + System.lineSeparator()
                                + "HTTPS Policy Enabled: " + checkHttpsOnlyPolicy(s3Client, bucketName) + System.lineSeparator()
                                + "KMS Encryption: " + checkEncryptionWithKms(s3Client, bucketName) + System.lineSeparator()
                                + "No Anonymous Uploads: " + checkAnonymousUploads(s3Client, bucketName)
                        );
    }


    private static boolean checkBlockPublicAccess(S3Client s3, String bucketName) {
        try {
            GetPublicAccessBlockResponse response = s3.getPublicAccessBlock(
                    GetPublicAccessBlockRequest.builder().bucket(bucketName).build());
            PublicAccessBlockConfiguration config = response.publicAccessBlockConfiguration();

            return config.blockPublicAcls() &&
                    config.blockPublicPolicy() &&
                    config.ignorePublicAcls() &&
                    config.restrictPublicBuckets();
        } catch (S3Exception e) {
            Log.error("Block Public Access Error: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private static boolean checkBucketPolicy(S3Client s3, String bucketName) {
        try {
            GetBucketPolicyResponse response = s3.getBucketPolicy(
                    GetBucketPolicyRequest.builder().bucket(bucketName).build());
            String policy = response.policy();
            if (policy != null) {
                return !(policy.contains("\"Principal\":\"*\"") && policy.contains("\"Effect\":\"Allow\""));
            } else {
                return true;
            }
        } catch (S3Exception e) {
            Log.error("Bucket Policy Error: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private static boolean checkBucketAcl(S3Client s3, String bucketName) {
        try {
            GetBucketAclResponse acl = s3.getBucketAcl(GetBucketAclRequest.builder().bucket(bucketName).build());
            List<Grant> grants = acl.grants();
            return grants.stream().noneMatch(g ->
                    g.grantee().typeAsString().equals("Group") &&
                            g.grantee().uri() != null &&
                            g.grantee().uri().contains("AllUsers"));
        } catch (S3Exception e) {
            Log.error("Bucket ACL Error: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private static boolean checkEncryption(S3Client s3, String bucketName) {
        try {
            GetBucketEncryptionResponse response = s3.getBucketEncryption(
                    GetBucketEncryptionRequest.builder().bucket(bucketName).build());
            return !response.serverSideEncryptionConfiguration().rules().isEmpty();
        }  catch (S3Exception e) {
            Log.error("Encryption Verification Error: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private static boolean checkVersioning(S3Client s3, String bucketName) {
        try {
            GetBucketVersioningResponse versioning = s3.getBucketVersioning(
                    GetBucketVersioningRequest.builder().bucket(bucketName).build());
            return "Enabled".equals(versioning.statusAsString());
        } catch (S3Exception e) {
            Log.error("Versioning Verification Error: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private static boolean checkLogging(S3Client s3, String bucketName) {
        try {
            GetBucketLoggingResponse logging = s3.getBucketLogging(
                    GetBucketLoggingRequest.builder().bucket(bucketName).build());
            return logging.loggingEnabled() != null;
        } catch (S3Exception e) {
            Log.error("Logging Verification Error: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private static boolean checkMfaDelete(S3Client s3, String bucketName) {
        try {
            GetBucketVersioningResponse response = s3.getBucketVersioning(
                    GetBucketVersioningRequest.builder().bucket(bucketName).build());

            return "Enabled".equalsIgnoreCase(response.mfaDeleteAsString());
        } catch (S3Exception e) {
            Log.error("MFA Delete Check Error: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private static boolean checkHttpsOnlyPolicy(S3Client s3, String bucketName) {
        try {
            GetBucketPolicyResponse response = s3.getBucketPolicy(
                    GetBucketPolicyRequest.builder().bucket(bucketName).build());
            String policy = response.policy();
            return policy.contains("\"Condition\"") && policy.contains("\"aws:SecureTransport\"") && policy.contains("\"false\"");
        } catch (S3Exception e) {
            Log.error("Https Policy enabled Error: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private static boolean checkEncryptionWithKms(S3Client s3, String bucketName) {
        try {
            GetBucketEncryptionResponse response = s3.getBucketEncryption(
                    GetBucketEncryptionRequest.builder().bucket(bucketName).build());

            return response.serverSideEncryptionConfiguration().rules().stream().anyMatch(rule ->
                    rule.applyServerSideEncryptionByDefault().sseAlgorithmAsString().equals("aws:kms")
            );
        } catch (S3Exception e) {
            Log.error("KMS Encryption Verification Error: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private static boolean checkAnonymousUploads(S3Client s3, String bucketName) {
        try {
            GetBucketPolicyResponse response = s3.getBucketPolicy(
                    GetBucketPolicyRequest.builder().bucket(bucketName).build());
            String policy = response.policy();
            return !(policy.contains("\"Principal\":\"*\"") && policy.contains("\"Action\":\"s3:PutObject\""));
        } catch (S3Exception e) {
            Log.error("Anonymous Uploads Verification Error: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }
}
