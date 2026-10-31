import {Badge, Button, mergeClasses, Text} from "@fluentui/react-components";
import {
    InformationRequestTemplateStatus,
    InformationRequestTemplateVersionStandingDto,
} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../../information-requests/shared/informationRequestFormatting.ts";
import {useTemplateVersionListStyles} from "./TemplateVersionListStyles.tsx";

interface TemplateVersionListProps {
    versions: InformationRequestTemplateVersionStandingDto[];
    shownVersionNumber?: number | null;
    onView?: (versionNumber: number) => void;
}

const standingText = (version: InformationRequestTemplateVersionStandingDto): string => {
    const name = `Version ${version.versionNumber}`;
    if (version.status === InformationRequestTemplateStatus.RETIRED) {
        return version.retiredAt ? `${name}, retired on ${formatInformationRequestTime(version.retiredAt)}` : `${name}, retired`;
    }
    return version.publishedAt ? `${name}, published on ${formatInformationRequestTime(version.publishedAt)}` : `${name}, published`;
};

const TemplateVersionList = ({versions, shownVersionNumber = null, onView}: TemplateVersionListProps) => {
    const styles = useTemplateVersionListStyles();

    return (
        <ul id={"information-request-template-version-list"}
            aria-label={"Versions of this Template"}
            className={styles.list}>
            {versions.map(version => {
                const retired = version.status === InformationRequestTemplateStatus.RETIRED;
                const shown = version.versionNumber === shownVersionNumber;
                const rowId = `information-request-template-version-${version.versionNumber}`;
                return (
                    <li key={version.versionNumber}
                        id={rowId}
                        className={mergeClasses(styles.row, retired && styles.retired)}>
                        <Text id={`${rowId}-standing`}>{standingText(version)}</Text>
                        {shown && (
                            <Badge id={`${rowId}-shown`}
                                   appearance={"tint"}
                                   color={"informative"}>
                                Shown
                            </Badge>
                        )}
                        {onView && !shown && (
                            <Button id={`${rowId}-view`}
                                    appearance={"secondary"}
                                    shape={"circular"}
                                    size={"small"}
                                    onClick={() => onView(version.versionNumber)}>
                                {`View version ${version.versionNumber}`}
                            </Button>
                        )}
                    </li>
                );
            })}
        </ul>
    );
};

export default TemplateVersionList;
