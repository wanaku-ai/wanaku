/**
 * Template that specifically maps to the resolved readme file. This template is not used when 'readme' is set to 'none'.
 */
export function readme(page) {
    const md = [];
    if (!this.options.getValue('hidePageHeader')) {
        md.push(this.partials.header());
    }
    if (!this.options.getValue('hideBreadcrumbs')) {
        md.push(this.partials.breadcrumbs());
    }
    if (page.model.readme) {
        md.push(this.helpers.getCommentParts(page.model.readme));
    }
    md.push(this.partials.footer());
    return md.join('\n\n');
}
