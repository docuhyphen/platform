import {Button, Text} from "@fluentui/react-components";
import {InformationRequestSubjectDto} from "../../../models/models.tsx";
import {usePrivacyPanelStyles} from "../privacy/PrivacyPanelStyles.tsx";
import {subjectLabel} from "../privacy/privacyLabels.ts";
import {formattedTime} from "../operationsLabels.ts";

interface PrivacySubjectListProps
{
    subjects: InformationRequestSubjectDto[];
    busy: boolean;
    onRecord: (subject: InformationRequestSubjectDto) => void;
}

const PrivacySubjectList = ({subjects, busy, onRecord}: PrivacySubjectListProps) =>
{
    const styles = usePrivacyPanelStyles();

    return (
        <div id={"information-request-privacy-subjects"}
             className={styles.section}>
            <Text id={"information-request-privacy-subjects-title"}
                  as={"h3"}
                  size={400}
                  weight={"semibold"}
                  className={styles.heading}>
                Subjects
            </Text>
            {subjects.length === 0 && (
                <Text id={"information-request-privacy-subjects-empty"}>No request has named a subject yet.</Text>
            )}
            {subjects.length > 0 && (
                <ul id={"information-request-privacy-subjects-list"}
                    aria-labelledby={"information-request-privacy-subjects-title"}
                    className={styles.list}>
                    {subjects.map(subject =>
                    {
                        const id = `information-request-privacy-subject-${subject.id}`;
                        const label = subjectLabel(subject);
                        return (
                            <li id={id}
                                key={subject.id}
                                className={styles.row}>
                                <div id={`${id}-text`}
                                     className={styles.text}>
                                    <Text id={`${id}-label`}
                                          weight={"semibold"}>
                                        {label}
                                    </Text>
                                    <Text id={`${id}-created`}
                                          size={200}
                                          className={styles.muted}>
                                        {`First named ${formattedTime(subject.createdAt)}`}
                                    </Text>
                                </div>
                                <Button id={`${id}-record`}
                                        appearance={"secondary"}
                                        shape={"circular"}
                                        disabled={busy}
                                        aria-label={`Record a privacy request for ${label}`}
                                        onClick={() => onRecord(subject)}>
                                    Record a request
                                </Button>
                            </li>
                        );
                    })}
                </ul>
            )}
        </div>
    );
};

export default PrivacySubjectList;
