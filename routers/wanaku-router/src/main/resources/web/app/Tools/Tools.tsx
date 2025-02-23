import * as React from "react";
import {
  Bullseye,
  Button,
  Card,
  CardHeader,
  CardTitle,
  CardBody,
  Dropdown,
  DropdownItem,
  DropdownList,
  EmptyState,
  EmptyStateHeader,
  EmptyStateIcon,
  EmptyStateFooter,
  EmptyStateVariant,
  EmptyStateActions,
  Gallery,
  MenuToggle,
  OverflowMenu,
  OverflowMenuItem,
  PageSection,
  PageSectionVariants,
  Pagination,
  TextContent,
  Text,
  Toolbar,
  ToolbarItem,
  ToolbarContent,
  MenuToggleElement,
} from "@patternfly/react-core";
import useFetch from "react-fetch-hook";
import TrashIcon from "@patternfly/react-icons/dist/esm/icons/trash-icon";
import PlusCircleIcon from "@patternfly/react-icons/dist/esm/icons/plus-circle-icon";
import EllipsisVIcon from "@patternfly/react-icons/dist/esm/icons/ellipsis-v-icon";
// import { data } from "@patternfly/react-core/src/demos/CardView/examples/CardViewData.jsx";

const Tools: React.FunctionComponent = () => {
  const totalItemCount = 10;
  // const [cardData, setCardData] = React.useState(data);
  const [page, setPage] = React.useState(1);
  const [perPage, setPerPage] = React.useState(10);
  const [filters, setFilters] = React.useState<Record<string, string[]>>({
    products: [],
  });
  const [state, setState] = React.useState({});
  const { isLoading, data } = useFetch("/api/v1/tools/list", {
    formatter: async (response) => {
      const text = await response.text();
      return JSON.parse(text);
    },
  });
  if (isLoading) return <div>Loading...</div>;

  console.log(data);

  interface ProductType {
    id: number;
    name: string;
    icon: string;
    description: string;
  }

  const deleteItem = (item: ProductType) => {
    const filter = (getter) => (val) => getter(val) !== item.id;

    // setCardData(cardData.filter(filter(({ id }) => id)));
    // TODO: Implement deleteItem
  };

  const onSetPage = (_event: any, pageNumber: number) => {
    setPage(pageNumber);
  };

  const onPerPageSelect = (_event: any, perPage: number) => {
    setPerPage(perPage);
    setPage(1);
  };

  const onCardKebabDropdownToggle = (
    event:
      | React.MouseEvent<HTMLButtonElement, MouseEvent>
      | React.MouseEvent<HTMLDivElement, MouseEvent>,
    key: string
  ) => {
    setState({
      [key]: !state[key as keyof Object],
    });
  };

  const onDelete = (type = "", _id = "") => {
    if (type) {
      setFilters(filters);
    } else {
      setFilters({ products: [] });
    }
  };

  const renderPagination = () => {
    const defaultPerPageOptions = [
      {
        title: "1",
        value: 1,
      },
      {
        title: "5",
        value: 5,
      },
      {
        title: "10",
        value: 10,
      },
    ];

    return (
      <Pagination
        itemCount={totalItemCount}
        page={page}
        perPage={perPage}
        perPageOptions={defaultPerPageOptions}
        onSetPage={onSetPage}
        onPerPageSelect={onPerPageSelect}
        variant="top"
        isCompact
      />
    );
  };

  const toolbarItems = (
    <React.Fragment>
      <ToolbarItem variant="overflow-menu">
        <OverflowMenu breakpoint="md">
          <OverflowMenuItem>
            <Button variant="primary">Add tool</Button>
          </OverflowMenuItem>
        </OverflowMenu>
      </ToolbarItem>
      <ToolbarItem variant="pagination" align={{ default: "alignRight" }}>
        {renderPagination()}
      </ToolbarItem>
    </React.Fragment>
  );

  const filtered = 
    filters.products.length > 0
      ? data.filter(
          (card: { name: string }) =>
            filters.products.length === 0 ||
            filters.products.includes(card.name)
        )
      : data.slice(
          (page - 1) * perPage,
          perPage === 1 ? page * perPage : page * perPage - 1
        );

  return (
    <React.Fragment>
      <PageSection variant={PageSectionVariants.light}>
        <TextContent>
          <Text component="h1">Tools</Text>
          <Text component="p">Description</Text>
        </TextContent>
        <Toolbar id="toolbar-group-types" clearAllFilters={onDelete}>
          <ToolbarContent>{toolbarItems}</ToolbarContent>
        </Toolbar>
      </PageSection>
      <PageSection isFilled>
        <Gallery hasGutter aria-label="Selectable card container">
          <Card isCompact>
            <Bullseye>
              <EmptyState variant={EmptyStateVariant.xs}>
                <EmptyStateHeader
                  headingLevel="h2"
                  titleText="Add a new tool"
                  icon={<EmptyStateIcon icon={PlusCircleIcon} />}
                />
                <EmptyStateFooter>
                  <EmptyStateActions>
                    <Button variant="link">Add tool</Button>
                  </EmptyStateActions>
                </EmptyStateFooter>
              </EmptyState>
            </Bullseye>
          </Card>
          {filtered.map((product, key) => (
            <Card
              isCompact
              isClickable
              isSelectable
              key={product.name}
              id={product.name.replace(/ /g, "-")}
            >
              <CardHeader
                actions={{
                  actions: (
                    <>
                      <Dropdown
                        isOpen={!!state[key]}
                        onOpenChange={(isOpen) => setState({ [key]: isOpen })}
                        toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                          <MenuToggle
                            ref={toggleRef}
                            aria-label={`${product.name} actions`}
                            variant="plain"
                            onClick={(e) => {
                              onCardKebabDropdownToggle(e, key.toString());
                            }}
                            isExpanded={!!state[key]}
                          >
                            <EllipsisVIcon />
                          </MenuToggle>
                        )}
                        popperProps={{ position: "right" }}
                      >
                        <DropdownList>
                          <DropdownItem
                            key="trash"
                            onClick={() => {
                              deleteItem(product);
                            }}
                          >
                            <TrashIcon />
                            Delete
                          </DropdownItem>
                        </DropdownList>
                      </Dropdown>
                    </>
                  ),
                }}
              >
                {product.type}
              </CardHeader>
              <CardTitle>{product.name}</CardTitle>
              <CardBody>{product.description}</CardBody>
            </Card>
          ))}
        </Gallery>
      </PageSection>
      <PageSection
        isFilled={false}
        stickyOnBreakpoint={{ default: "bottom" }}
        padding={{ default: "noPadding" }}
        variant="light"
      >
        <Pagination
          itemCount={totalItemCount}
          page={page}
          perPage={perPage}
          onPerPageSelect={onPerPageSelect}
          onSetPage={onSetPage}
          variant="bottom"
        />
      </PageSection>
    </React.Fragment>
  );
};

export { Tools };

// let Support: React.FunctionComponent<ISupportProps> = () => (
//   <PageSection>
//     <EmptyState variant={EmptyStateVariant.full}>
//       <EmptyStateHeader titleText="Empty State (Stub Support Module)" icon={<EmptyStateIcon icon={CubesIcon} />} headingLevel="h1" />
//       <EmptyStateBody>
//         <TextContent>
//           <Text component="p">
//             This represents an the empty state pattern in Patternfly 4. Hopefully it&apos;s simple enough to use but
//             flexible enough to meet a variety of needs.
//           </Text>
//           <Text component={TextVariants.small}>
//             This text has overridden a css component variable to demonstrate how to apply customizations using
//             PatternFly&apos;s global variable API.
//           </Text>
//         </TextContent>
//       </EmptyStateBody><EmptyStateFooter>
//       <Button variant="primary">Primary Action</Button>
//       <EmptyStateActions>
//         <Button variant="link">Multiple</Button>
//         <Button variant="link">Action Buttons</Button>
//         <Button variant="link">Can</Button>
//         <Button variant="link">Go here</Button>
//         <Button variant="link">In the secondary</Button>
//         <Button variant="link">Action area</Button>
//       </EmptyStateActions>
//     </EmptyStateFooter></EmptyState>
//   </PageSection>
// );
