import {useState} from "react";
import {Spinner, Text} from "@fluentui/react-components";
import {InformationRequestSubjectDto, InformationRequestSubjectRestrictionDto} from "../../../models/models.tsx";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import LiftRestrictionDialog from "../lift-restriction-dialog/LiftRestrictionDialog.tsx";
import PrivacyRequestList from "../privacy-request-list/PrivacyRequestList.tsx";
import PrivacySubjectList from "../privacy-subject-list/PrivacySubjectList.tsx";
import RecordPrivacyRequestDialog from "../record-privacy-request-dialog/RecordPrivacyRequestDialog.tsx";
import SubjectRestrictionList from "../subject-restriction-list/SubjectRestrictionList.tsx";
import {subjectLabel} from "./privacyLabels.ts";
import {usePrivacy} from "./usePrivacy.ts";
import {usePrivacyPanelStyles} from "./PrivacyPanelStyles.tsx";

const PrivacyPanel = () =>
{
    const styles = usePrivacyPanelStyles();
    const state = usePrivacy();
    const records = state.records;
    const [recording, setRecording] = useState<InformationRequestSubjectDto | null>(null);
    const [lifting, setLifting] = useState<InformationRequestSubjectRestrictionDto | null>(null);
    const labelOf = (subjectId: string): string =>
    {
        const subject = records?.subjects.find(candidate => candidate.id === subjectId);
        return subject ? subjectLabel(subject) : "Unknown subject";
    };

    return (
        <section id={"information-request-privacy"}
                 aria-labelledby={"information-request-privacy-title"}
                 className={styles.panel}>
            <Text id={"information-request-privacy-title"}
                  as={"h2"}
                  size={500}
                  weight={"semibold"}
                  className={styles.heading}>
                Privacy
            </Text>
            <Text id={"information-request-privacy-explanation"}
                  className={styles.muted}>
                Record what a subject asked for about the Information Requests held about them. Each request is
                kept with its purpose, its policy basis, and what it did to each record.
            </Text>
            {state.loadError && (
                <InformationRequestLoadError id={"information-request-privacy-error"}
                                             message={state.loadError}
                                             onRetry={() => void state.reload()}/>
            )}
            <Text id={"information-request-privacy-status"}
                  role={"status"}
                  aria-live={"polite"}>
                {state.notice ?? ""}
            </Text>
            {!records && !state.loadError && (
                <Spinner id={"information-request-privacy-loading"}
                         size={"medium"}
                         label={"Loading privacy records"}/>
            )}
            {records && (
                <>
                    <PrivacySubjectList subjects={records.subjects}
                                        busy={state.busy}
                                        onRecord={setRecording}/>
                    <SubjectRestrictionList restrictions={records.restrictions}
                                            labelOf={labelOf}
                                            busy={state.busy}
                                            onLift={setLifting}/>
                    <PrivacyRequestList requests={records.requests}
                                        labelOf={labelOf}/>
                </>
            )}
            {recording && (
                <RecordPrivacyRequestDialog subjectId={recording.id}
                                            subjectLabel={subjectLabel(recording)}
                                            busy={state.busy}
                                            onConfirm={state.record}
                                            onDone={() => setRecording(null)}/>
            )}
            {lifting && (
                <LiftRestrictionDialog busy={state.busy}
                                       onConfirm={reason => state.lift(lifting, reason)}
                                       onDone={() => setLifting(null)}/>
            )}
        </section>
    );
};

export default PrivacyPanel;
