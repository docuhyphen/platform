import {Button, Text} from "@fluentui/react-components";
import {focusRequirement} from "../../submission/submissionReview.ts";
import {SectionSummary} from "../sectionSummaries.ts";
import {useResponseSectionNavigationStyles} from "./ResponseSectionNavigationStyles.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";

interface Props
{
    summaries: SectionSummary[];
}

const progressText = (summary: SectionSummary): string =>
    summary.required === 0 ? "Nothing required" : `${formatInformationRequestCount(summary.answered)} of ${formatInformationRequestCount(summary.required)} required answered`;

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
                                onClick={() => focusRequirement(summary.occurrencePath, summary.requirementKey)}>
                            <Text weight={"semibold"}>{summary.title}</Text>
                            <Text size={200}
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
