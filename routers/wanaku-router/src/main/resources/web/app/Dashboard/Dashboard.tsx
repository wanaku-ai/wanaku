import * as React from 'react';
import {PageSection, Title, Spinner, TextContent, Text, TextVariants} from '@patternfly/react-core';
import useFetch from "react-fetch-hook";

const Dashboard: React.FunctionComponent = () => {
  const {isLoading, data} = useFetch("/api/v1/tools/list", {
    formatter: r => r.text()
  });
  return (
    <>
      <PageSection>
        <Title headingLevel="h1" size="lg">Dashboard Page Title!</Title>
      </PageSection>
      <PageSection isFilled={true}>
        <TextContent>
          <Text component={TextVariants.p}>
            Hello World
            {isLoading && <Spinner size="md"/>}
            {data && <Text component={TextVariants.p}>{data}</Text>}
          </Text>
        </TextContent>
      </PageSection>
    </>
  )
}

export {Dashboard};
