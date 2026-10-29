import { useMemo, useState } from 'react';

export const PAGE_SIZES = [10, 25, 50, 100];

/**
 * Filters a list by a case-insensitive search term and returns the current page.
 * `searchableText` returns the values of an item that the search term can match.
 */
export function useSearchPagination<T>(
  items: T[],
  searchableText: (item: T) => (string | null | undefined)[],
) {
  const [searchTerm, setSearchTermState] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZES[1]);

  const filteredItems = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    if (!term) return items;
    return items.filter((item) =>
      searchableText(item).some((value) => value?.toLowerCase().includes(term)),
    );
  }, [items, searchTerm, searchableText]);

  // Move to the last valid page when the list shrinks, for example after a refresh.
  // Storing the clamped page keeps the table there if the list grows again.
  const lastPage = Math.max(1, Math.ceil(filteredItems.length / pageSize));
  if (page > lastPage) setPage(lastPage);
  const currentPage = Math.min(page, lastPage);
  const pageItems = filteredItems.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const setSearchTerm = (term: string) => {
    setSearchTermState(term);
    setPage(1);
  };

  const onPageChange = ({ page, pageSize }: { page: number; pageSize: number }) => {
    setPage(page);
    setPageSize(pageSize);
  };

  return {
    searchTerm,
    setSearchTerm,
    page: currentPage,
    pageSize,
    onPageChange,
    pageItems,
    filteredCount: filteredItems.length,
  };
}
