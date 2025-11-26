# Security Policy

## Supported Versions

We release patches for security vulnerabilities in the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 0.0.x   | :white_check_mark: |
| < 0.0.1 | :x:                |

## Reporting a Vulnerability

We take the security of Wanaku seriously. If you believe you have found a security vulnerability, please report it to us responsibly (via contact@wanaku.ai).

### How to Report

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, please report security vulnerabilities by emailing the project maintainers. You can find contact information in the project repository.

Please include the following information in your report:

- Type of vulnerability
- Full paths of source file(s) related to the vulnerability
- Location of the affected source code (tag/branch/commit or direct URL)
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the vulnerability, including how an attacker might exploit it

### What to Expect

- You will receive an acknowledgment within 48 hours
- We will investigate and provide an estimated timeline for a fix
- We will notify you when the vulnerability is fixed
- We will publicly disclose the vulnerability after a fix is released

## Security Best Practices

When deploying Wanaku, please follow these security best practices:

### Authentication and Authorization

- Always use Keycloak or another OIDC provider for authentication
- Change default admin passwords immediately after setup
- Regenerate client secrets for the `wanaku-service` client in production
- Use strong, unique passwords for all service accounts

### Network Security

- Enable TLS/HTTPS for all external endpoints in production
- Configure CORS appropriately for your environment
- Use network policies to restrict access between services
- Never expose Keycloak or the router backend directly to the internet without proper security controls

### Secret Management

- Never commit secrets, passwords, or API keys to version control
- Use Kubernetes Secrets, Sealed Secrets, or external secret management tools
- Rotate secrets regularly
- Use environment-specific secrets for development and production

### Container Security

- Always use the latest stable version of Wanaku images
- Scan container images for vulnerabilities regularly
- Run containers with minimal privileges
- Use read-only file systems where possible

### Monitoring and Auditing

- Enable access logging for the router backend
- Monitor authentication failures and unusual access patterns
- Review audit logs regularly
- Set up alerts for suspicious activity

For more security configuration options, see the [Configuration Guide](docs/configurations.md).

## Acknowledgments

We appreciate the security research community's efforts in responsibly disclosing vulnerabilities.
