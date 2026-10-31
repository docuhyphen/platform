import {Badge, Text, Title3} from "@fluentui/react-components";
import {InformationRequestDocumentPlaceholderDto} from "../../../models/models.tsx";
import {formatInformationRequestSize} from "../../shared/informationRequestFormatting.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {useExpectedDocumentsPanelStyles} from "./ExpectedDocumentsPanelStyles.tsx";

interface Props {
    placeholders: InformationRequestDocumentPlaceholderDto[];
    error?: string | null;
    onRetry?: () => void;
}

const libraryReferenceOf = (placeholder: InformationRequestDocumentPlaceholderDto): string | null => {
    const name = placeholder.libraryTitle ?? placeholder.libraryFileName;
    if (!name) return null;
    const file = placeholder.libraryFileName && placeholder.libraryFileName !== name ? `, ${placeholder.libraryFileName}` : "";
    const size = placeholder.libraryFileSizeBytes !== undefined && placeholder.libraryFileSizeBytes !== null
        ? `, ${formatInformationRequestSize(placeholder.libraryFileSizeBytes)}`
        : "";
    return `From the Document Library: ${name}${file}${size}`;
};

const ExpectedDocumentsPanel = ({placeholders, error = null, onRetry = () => undefined}: Props) => {
    const styles = useExpectedDocumentsPanelStyles();
    if (error) {
        return (
            <InformationRequestLoadError id={"information-request-expected-documents-load-error"}
                                         message={error}
                                         onRetry={onRetry}/>
        );
    }
    if (placeholders.length === 0) return null;

    return (
        <section id={"information-request-expected-documents"}
                 aria-labelledby={"information-request-expected-documents-title"}
                 className={styles.panel}>
            <Title3 id={"information-request-expected-documents-title"}
                    as={"h2"}>
                Expected documents
            </Title3>
            <Text id={"information-request-expected-documents-help"}
                  className={styles.muted}>
                The Blueprint this request was created from listed these documents. They are kept as they
                were when the request was created.
            </Text>
            <ul id={"information-request-expected-documents-list"}
                className={styles.list}>
                {placeholders.map(placeholder => {
                    const reference = libraryReferenceOf(placeholder);
                    return (
                        <li id={`information-request-expected-document-${placeholder.id}`}
                            key={placeholder.id}
                            className={styles.item}>
                            <div id={`information-request-expected-document-${placeholder.id}-heading`}
                                 className={styles.heading}>
                                <Text id={`information-request-expected-document-${placeholder.id}-title`}
                                      weight={"semibold"}
                                      className={styles.title}>
                                    {placeholder.title}
                                </Text>
                                <Badge id={`information-request-expected-document-${placeholder.id}-requiredness`}
                                       appearance={"tint"}
                                       color={placeholder.required ? "brand" : "informative"}>
                                    {placeholder.required ? "Required" : "Optional"}
                                </Badge>
                                {placeholder.restrictType && placeholder.restrictedType && (
                                    <Badge id={`information-request-expected-document-${placeholder.id}-type`}
                                           appearance={"outline"}>
                                        {`${placeholder.restrictedType} only`}
                                    </Badge>
                                )}
                            </div>
                            {reference && (
                                <Text id={`information-request-expected-document-${placeholder.id}-library`}
                                      size={200}
                                      className={styles.muted}>
                                    {reference}
                                </Text>
                            )}
                        </li>
                    );
                })}
            </ul>
        </section>
    );
};

export default ExpectedDocumentsPanel;
