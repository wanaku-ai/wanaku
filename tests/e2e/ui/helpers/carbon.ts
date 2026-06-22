export const Carbon = {
  dataTable: '.cds--data-table',
  tableRow: 'table tbody tr',
  tableToolbar: '.cds--table-toolbar',

  modal: '.cds--modal.is-visible',
  modalHeading: '.cds--modal-header__heading',
  modalFooterPrimary: '.cds--modal-footer .cds--btn--primary',
  modalFooterSecondary: '.cds--modal-footer .cds--btn--secondary',

  textInput: (id: string) => `#${id}`,
  textArea: (id: string) => `#${id}`,

  toastError: '.cds--toast-notification--error',
  toastSuccess: '.cds--toast-notification--success',

  buttonWithText: (text: string) => `button:has-text("${text}")`,
  iconButton: (label: string) =>
    `.cds--icon-tooltip:has(.cds--tooltip-content:text-is("${label}")) button`,

  skeleton: '.cds--skeleton',
  skeletonTable: '.cds--data-table-container.cds--skeleton',
};
