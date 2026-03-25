/**
 *  The link element
 * @param label The text to display for the link
 * @param url The url to link to
 */
export function link(label, url) {
    return url ? `[${label.trim()}](${url})` : '';
}
