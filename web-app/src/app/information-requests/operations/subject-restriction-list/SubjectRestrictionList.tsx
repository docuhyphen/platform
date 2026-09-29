import {Button, Text} from "@fluentui/react-components";
import {InformationRequestSubjectRestrictionDto} from "../../../models/models.tsx";
import {usePrivacyPanelStyles} from "../privacy/PrivacyPanelStyles.tsx";
import {formattedTime} from "../operationsLabels.ts";

interface SubjectRestrictionListProps
{
    restrictions: InformationRequestSubjectRestrictionDto[];
    labelOf: (subjectId: string) => string;
    busy: boolean;
    onLift: (restriction: InformationRequestSubjectRestrictionDto) => void;
}

const SubjectRestrictionList = ({restrictions, labelOf, busy, onLift}: SubjectRestrictionListProps) =>
{
    const styles = usePrivacyPanelStyles();

    return (
        <div id={"information-request-privacy-restrictions"}
             className={styles.section}>
            <Text id={"information-request-privacy-restrictions-title"}
                  as={"h3"}
                  size={400}
                  weight={"semibold"}
                  className={styles.heading}>
                Restrictions
            </Text>
            <Text id={"information-request-privacy-restrictions-explanation"}
                  className={styles.muted}>
                While a restriction holds, no answer about that subject is promoted or reused.
            </Text>
            {restrictions.length === 0 && (
                <Text id={"information-request-privacy-restrictions-empty"}>No subject is restricted.</Text>
            )}
            {restrictions.length > 0 && (
                <ul id={"information-request-privacy-restrictions-list"}
                    aria-labelledby={"information-request-privacy-restrictions-title"}
                    className={styles.list}>
                    {restrictions.map(restriction =>
                    {
                        const id = `information-request-privacy-restriction-${restriction.id}`;
                        const label = labelOf(restriction.subjectIdentityRefId);
                        return (
                            <li id={id}
                                key={restriction.id}
                                className={styles.row}>
                                <div id={`${id}-text`}
                                     className={styles.text}>
                                    <Text id={`${id}-label`}
                                          weight={"semibold"}>
                                        {label}
                                    </Text>
                                    <Text id={`${id}-state`}
                                          size={200}
                                          className={styles.muted}>
                                        {restriction.liftedAt
                                            ? `Restricted ${formattedTime(restriction.restrictedAt)}, lifted ${formattedTime(restriction.liftedAt)}: ${restriction.liftReasonCode ?? ""}`
                                            : `Restricted ${formattedTime(restriction.restrictedAt)}`}
                                    </Text>
                                </div>
                                {!restriction.liftedAt && (
                                    <Button id={`${id}-lift`}
                                            appearance={"secondary"}
                                            shape={"circular"}
                                            disabled={busy}
                                            aria-label={`Lift the restriction on ${label}`}
                                            onClick={() => onLift(restriction)}>
                                        Lift
                                    </Button>
                                )}
                            </li>
                        );
                    })}
                </ul>
            )}
        </div>
    );
};

export default SubjectRestrictionList;
