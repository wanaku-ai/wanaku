# AI Coding Instructions for This Repository

This document provides guidelines for effectively using AI coding tools (such as GitHub Copilot, ChatGPT, Claude Code, etc.) in the development of this project.

---

## 1. Purpose of Using AI Coding Tools

- Accelerate code generation for standard tasks and boilerplate.
- Aid with code review, refactoring, and documentation.
- Support rapid prototyping and exploration of new features.
- Assist with test case generation and bug detection.

---

## 2. Prompt Engineering Guidelines

- Be specific: Clearly describe what you want, including context and intent.
- Break complex requests into steps.
- Reference relevant files, functions, or modules.
- Use concrete examples or sample inputs/outputs where possible.
- If you need a certain style, framework, or convention, mention it in your prompt.

**Example Good Prompts:**
- “Generate a function that validates user email addresses according to RFC 5322.”
- “Write a unit test for the `processOrder` method in `order.js`.”
- “Refactor the following code to use async/await instead of callbacks: ...”

---

## 3. Quality Control for AI-Generated Code

- All AI-generated code must be reviewed by a human before merging.
- Test all generated code, especially for edge cases and security implications.
- Prefer clarity and maintainability over cleverness.
- Check for license compatibility before using AI-suggested code snippets from external sources.
- Document any limitations or uncertainties in the generated code.

---

## 4. Team Collaboration and Workflow

- Communicate when using AI tools for significant changes (e.g., in PR descriptions).
- Use comments to flag code that was AI-generated for transparency.
- Encourage pair programming with AI as a “second reviewer.”
- Integrate AI suggestions incrementally and with context, not by bulk-pasting large code blocks.

---

## 5. Known Limitations & Warnings

- AI may hallucinate APIs, function names, or incorrect logic—always verify.
- Generated code may not follow all project conventions unless prompted.
- Sensitive or proprietary code should not be shared with third-party AI tools.
- Avoid using AI for tasks requiring deep domain knowledge unless results are carefully reviewed.

---

## 6. Example Prompts for This Project

- “Add a REST endpoint to fetch all products from the database.”
- “Document the purpose and parameters of the `UserAuth` class.”
- “Suggest unit tests for the `calculateDiscount` function.”
- “Refactor the main loop in `app.js` to improve performance.”

---

## 7. Continuous Improvement

- Update this file with new best practices, limitations, and prompt examples as team experience grows.
- Share successful prompt patterns and common pitfalls in team meetings or documentation.

---

*For questions or suggestions regarding these guidelines, please open an issue or contact the repository maintainers.*