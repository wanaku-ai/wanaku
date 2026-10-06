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

  // Visible part of a Carbon toggle. It covers the hidden <button role="switch">.
  toggleAppearance: '.cds--toggle__appearance',

  buttonWithText: (text: string) => `button:has-text("${text}")`,

  skeleton: '.cds--skeleton',
  skeletonTable: '.cds--data-table-container.cds--skeleton',
};
