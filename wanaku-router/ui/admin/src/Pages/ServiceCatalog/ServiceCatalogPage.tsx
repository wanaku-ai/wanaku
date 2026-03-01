import React, {useCallback, useEffect, useState} from "react";
import {ToastNotification} from "@carbon/react";
import {ServiceCatalogCards} from "./ServiceCatalogCards";
import {useServiceCatalog} from "../../hooks/api/use-service-catalog";
import "./ServiceCatalogPage.scss";

interface ServiceCatalogSummary {
  id: string;
  name: string;
  icon?: string;
  description: string;
  services: string[];
}

export const ServiceCatalogPage: React.FC = () => {
  const [catalogs, setCatalogs] = useState<ServiceCatalogSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const { listServiceCatalogs, getServiceCatalog, removeServiceCatalog } = useServiceCatalog();

  const fetchCatalogs = useCallback(
    async (search?: string) => {
      try {
        const result = await listServiceCatalogs(search);
        setCatalogs((result.data as ServiceCatalogSummary[]) || []);
        setIsLoading(false);
      } catch (error) {
        console.error("Error fetching service catalogs:", error);
        setErrorMessage("Failed to load service catalogs");
        setIsLoading(false);
      }
    },
    [listServiceCatalogs]
  );

  useEffect(() => {
    fetchCatalogs();
  }, [fetchCatalogs]);

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => setErrorMessage(null), 10000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);

  useEffect(() => {
    if (successMessage) {
      const timer = setTimeout(() => setSuccessMessage(null), 5000);
      return () => clearTimeout(timer);
    }
  }, [successMessage]);

  if (isLoading) {
    return <div>Loading...</div>;
  }

  const handleDelete = async (name: string) => {
    try {
      await removeServiceCatalog(name);
      setSuccessMessage(`Service catalog '${name}' deleted successfully`);
      fetchCatalogs();
    } catch (error) {
      console.error("Error deleting service catalog:", error);
      setErrorMessage(`Failed to delete service catalog '${name}'`);
    }
  };

  const handleSearch = (search: string) => {
    fetchCatalogs(search || undefined);
  };

  const handleGetDetail = async (name: string) => {
    try {
      const result = await getServiceCatalog(name);
      return result.data;
    } catch (error) {
      console.error("Error fetching catalog detail:", error);
      return null;
    }
  };

  return (
    <div className="service-catalog-page">
      {errorMessage && (
        <ToastNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          timeout={10000}
          style={{ float: "right" }}
        />
      )}
      {successMessage && (
        <ToastNotification
          kind="success"
          title="Success"
          subtitle={successMessage}
          onCloseButtonClick={() => setSuccessMessage(null)}
          timeout={5000}
          style={{ float: "right" }}
        />
      )}
      <h1 className="title">Service Catalog</h1>
      <p className="description">
        View and manage deployed service catalogs. Services are packaged via the CLI and contain
        Camel routes, Wanaku rules, and optional dependencies for one or more systems.
      </p>
      <ServiceCatalogCards
        catalogs={catalogs}
        onDelete={handleDelete}
        onSearch={handleSearch}
        getDetail={handleGetDetail}
      />
    </div>
  );
};
