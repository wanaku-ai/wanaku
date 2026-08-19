import React from "react";
import { SkeletonText } from "@carbon/react";

interface PageSkeletonProps {
  title: string;
}

export const PageSkeleton: React.FC<PageSkeletonProps> = ({ title }) => (
  <div>
    <h1 className="title">{title}</h1>
    <SkeletonText heading={false} lineCount={5} width="80%" />
  </div>
);
