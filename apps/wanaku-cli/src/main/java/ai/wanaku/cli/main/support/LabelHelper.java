package ai.wanaku.cli.main.support;

import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import ai.wanaku.capabilities.sdk.api.types.LabelsAwareEntity;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;

public final class LabelHelper {

    private LabelHelper() {}

    public static Map<String, String> parseLabels(List<String> labels, WanakuPrinter printer) {
        if (labels == null || labels.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, String> labelMap = new HashMap<>();
        for (String label : labels) {
            String[] parts = label.split("=", 2);
            if (parts.length == 2) {
                labelMap.put(parts[0].trim(), parts[1].trim());
            } else {
                printer.printErrorMessage(
                        String.format("Invalid label format: '%s'. Expected format: 'key=value'", label));
                return null;
            }
        }
        return labelMap;
    }

    public static int validateLabelExpression(
            String identifier, String labelExpression, String identifierOption, WanakuPrinter printer) {
        if (identifier != null && labelExpression != null) {
            printer.printErrorMessage(String.format(
                    "Cannot specify both %s and --label-expression. Use one or the other.", identifierOption));
            return BaseCommand.EXIT_ERROR;
        }

        if (identifier == null && labelExpression == null) {
            printer.printErrorMessage(String.format("Must specify either %s or --label-expression.", identifierOption));
            return BaseCommand.EXIT_ERROR;
        }

        return BaseCommand.EXIT_OK;
    }

    public static <T extends LabelsAwareEntity<String>> int addLabelsToEntity(
            T entity,
            Map<String, String> labelsToAdd,
            WanakuPrinter printer,
            Consumer<T> updater,
            String entityType,
            String identifier) {

        Map<String, String> existingLabels = entity.getLabels();
        if (existingLabels == null) {
            existingLabels = new HashMap<>();
        }

        int addedCount = 0;
        int updatedCount = 0;

        for (Map.Entry<String, String> entry : labelsToAdd.entrySet()) {
            if (existingLabels.containsKey(entry.getKey())) {
                String oldValue = existingLabels.get(entry.getKey());
                if (!oldValue.equals(entry.getValue())) {
                    printer.printInfoMessage(String.format(
                            "Updating label '%s': '%s' -> '%s'", entry.getKey(), oldValue, entry.getValue()));
                    updatedCount++;
                }
            } else {
                printer.printInfoMessage(String.format("Adding label '%s' = '%s'", entry.getKey(), entry.getValue()));
                addedCount++;
            }
            existingLabels.put(entry.getKey(), entry.getValue());
        }

        entity.setLabels(existingLabels);
        updater.accept(entity);

        printer.printSuccessMessage(String.format(
                "Labels updated for %s '%s' (%d added, %d updated)", entityType, identifier, addedCount, updatedCount));
        return BaseCommand.EXIT_OK;
    }

    public static <T extends LabelsAwareEntity<String>> int removeLabelsFromEntity(
            T entity,
            List<String> labelKeys,
            WanakuPrinter printer,
            Consumer<T> updater,
            String entityType,
            String identifier) {

        Map<String, String> existingLabels = entity.getLabels();
        if (existingLabels == null) {
            existingLabels = new HashMap<>();
        }

        int removedCount = 0;
        int notFoundCount = 0;

        for (String labelKey : labelKeys) {
            if (existingLabels.containsKey(labelKey)) {
                String removedValue = existingLabels.remove(labelKey);
                printer.printInfoMessage(String.format("Removed label '%s' (was: '%s')", labelKey, removedValue));
                removedCount++;
            } else {
                printer.printWarningMessage(String.format("Label '%s' not found, skipping", labelKey));
                notFoundCount++;
            }
        }

        if (removedCount > 0) {
            entity.setLabels(existingLabels);
            updater.accept(entity);
            printer.printSuccessMessage(String.format(
                    "Labels updated for %s '%s' (%d removed, %d not found)",
                    entityType, identifier, removedCount, notFoundCount));
        } else {
            printer.printWarningMessage("No labels were removed");
        }

        return BaseCommand.EXIT_OK;
    }

    public static <T extends LabelsAwareEntity<String>> int addLabelsByExpression(
            WanakuResponse<List<T>> listResponse,
            Map<String, String> labelsToAdd,
            WanakuPrinter printer,
            Consumer<T> updater,
            java.util.function.Function<T, String> identifierExtractor,
            String entityTypePlural,
            String labelExpression)
            throws IOException {

        List<T> matchingEntities = listResponse.data();

        if (matchingEntities == null || matchingEntities.isEmpty()) {
            printer.printWarningMessage(
                    String.format("No %s found matching label expression: %s", entityTypePlural, labelExpression));
            return BaseCommand.EXIT_OK;
        }

        printer.printInfoMessage(String.format(
                "Found %d %s matching label expression '%s'",
                matchingEntities.size(), entityTypePlural, labelExpression));

        int successCount = 0;
        int failureCount = 0;

        for (T entity : matchingEntities) {
            try {
                Map<String, String> existingLabels = entity.getLabels();
                if (existingLabels == null) {
                    existingLabels = new HashMap<>();
                }
                existingLabels.putAll(labelsToAdd);
                entity.setLabels(existingLabels);
                updater.accept(entity);
                printer.printSuccessMessage(" Updated: " + identifierExtractor.apply(entity));
                successCount++;
            } catch (WebApplicationException ex) {
                printer.printErrorMessage(" Failed to update: " + identifierExtractor.apply(entity));
                failureCount++;
            }
        }

        printer.printInfoMessage(
                String.format("Label update complete: %d succeeded, %d failed", successCount, failureCount));

        return failureCount > 0 ? BaseCommand.EXIT_ERROR : BaseCommand.EXIT_OK;
    }

    public static <T extends LabelsAwareEntity<String>> int removeLabelsByExpression(
            WanakuResponse<List<T>> listResponse,
            List<String> labelKeys,
            WanakuPrinter printer,
            Consumer<T> updater,
            java.util.function.Function<T, String> identifierExtractor,
            String entityTypePlural,
            String labelExpression)
            throws IOException {

        List<T> matchingEntities = listResponse.data();

        if (matchingEntities == null || matchingEntities.isEmpty()) {
            printer.printWarningMessage(
                    String.format("No %s found matching label expression: %s", entityTypePlural, labelExpression));
            return BaseCommand.EXIT_OK;
        }

        printer.printInfoMessage(String.format(
                "Found %d %s matching label expression '%s'",
                matchingEntities.size(), entityTypePlural, labelExpression));

        int successCount = 0;
        int failureCount = 0;

        for (T entity : matchingEntities) {
            try {
                Map<String, String> existingLabels = entity.getLabels();
                if (existingLabels == null) {
                    existingLabels = new HashMap<>();
                }

                boolean modified = false;
                for (String labelKey : labelKeys) {
                    if (existingLabels.remove(labelKey) != null) {
                        modified = true;
                    }
                }

                if (modified) {
                    entity.setLabels(existingLabels);
                    updater.accept(entity);
                    printer.printSuccessMessage(" Updated: " + identifierExtractor.apply(entity));
                    successCount++;
                } else {
                    printer.printInfoMessage(" No changes: " + identifierExtractor.apply(entity));
                }
            } catch (WebApplicationException ex) {
                printer.printErrorMessage(" Failed to update: " + identifierExtractor.apply(entity));
                failureCount++;
            }
        }

        printer.printInfoMessage(
                String.format("Label removal complete: %d succeeded, %d failed", successCount, failureCount));

        return failureCount > 0 ? BaseCommand.EXIT_ERROR : BaseCommand.EXIT_OK;
    }
}
