import {Button, Text} from "@fluentui/react-components";
import {focusRequirement} from "../../submission/submissionReview.ts";
import {SectionSummary} from "../sectionSummaries.ts";
import {useResponseSectionNavigationStyles} from "./ResponseSectionNavigationStyles.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";

interface Props
{
    summaries: SectionSummary[];
}

const progressText = (summary: SectionSummary): string => {
    if (summary.required > 0) {
        return `${formatInformationRequestCount(summary.answered)} of ${formatInformationRequestCount(summary.required)} required answered`;
    }
    return summary.confirmations > 0 ? "Confirm under Review and submit" : "Nothing required";
};

const ResponseSectionNavigation = ({summaries}: Props) =>
{
    const styles = useResponseSectionNavigationStyles();

    if (summaries.length === 0) return null;

    return (
        <nav id={"information-request-section-navigation"}
             aria-label={"Sections"}
             className={styles.navigation}>
            <ol id={"information-request-section-navigation-list"}
                className={styles.list}>
                {summaries.map(summary => (
                    <li key={summary.id}
                        id={`information-request-section-navigation-${summary.id}`}>
                        <Button id={`information-request-section-navigation-${summary.id}-open`}
                                appearance={"subtle"}
                                shape={"circular"}
                                className={styles.entry}
                                aria-label={`${summary.title}, ${progressText(summary)}`}
                                onClick={() => focusRequirement(summary.occurrencePath, summary.requirementKey, summary.requirementId)}>
                            <Text id={`information-request-section-navigation-${summary.id}-title`}
                                  weight={"semibold"}>
                                {summary.title}
                            </Text>
                            <Text id={`information-request-section-navigation-${summary.id}-progress`}
                                  size={200}
                                  className={styles.progress}>
                                {progressText(summary)}
                            </Text>
                        </Button>
                    </li>
                ))}
            </ol>
        </nav>
    );
};

export default ResponseSectionNavigation;
