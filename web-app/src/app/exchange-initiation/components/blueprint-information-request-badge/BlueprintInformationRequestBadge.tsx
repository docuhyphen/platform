import React from "react";
import {Badge} from "@fluentui/react-components";
import {BlueprintDefinitionSummaryDto} from "../../../models/models.tsx";
import {useBlueprintInformationRequestBadgeStyles} from "./BlueprintInformationRequestBadgeStyles.tsx";
import {pinnedVersionRetired} from "../../hooks/blueprintRequestStart.ts";

interface BlueprintInformationRequestBadgeProps {
    blueprint: BlueprintDefinitionSummaryDto;
}

const BlueprintInformationRequestBadge: React.FC<BlueprintInformationRequestBadgeProps> = ({blueprint}) => {
    const styles = useBlueprintInformationRequestBadgeStyles();

    if (!blueprint.informationRequestTemplateVersionId) {
        return null;
    }
    if (pinnedVersionRetired(blueprint)) {
        return (
            <Badge id={`blueprint-information-request-badge-${blueprint.id}`}
                   className={styles.badge}
                   appearance={"tint"}
                   color={"warning"}>
                Cannot start: its Template Version is retired
            </Badge>
        );
    }

    return (
        <Badge id={`blueprint-information-request-badge-${blueprint.id}`}
               className={styles.badge}
               appearance={"tint"}
               color={"informative"}>
            Starts an Information Request
        </Badge>
    );
};

export default BlueprintInformationRequestBadge;
