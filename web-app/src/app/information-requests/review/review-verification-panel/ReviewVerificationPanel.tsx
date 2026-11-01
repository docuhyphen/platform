import {useState} from "react";
import {Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {ShieldCheckmarkRegular} from "@fluentui/react-icons";
import {requestStepUp} from "../../../../services/stepUpBroker.ts";
import {useReviewVerificationPanelStyles} from "./ReviewVerificationPanelStyles.tsx";

interface Props {
    awaiting: number;
    onVerified: () => void;
}

const hiddenItems = (awaiting: number): string =>
    awaiting === 1
        ? "1 protected item is hidden until you verify again. Verifying lasts 5 minutes."
        : `${awaiting} protected items are hidden until you verify again. Verifying lasts 5 minutes.`;

const ReviewVerificationPanel = ({awaiting, onVerified}: Props) => {
    const styles = useReviewVerificationPanelStyles();
    const [busy, setBusy] = useState(false);
    const [unfinished, setUnfinished] = useState(false);

    if (awaiting === 0) return null;

    const verify = async () => {
        setBusy(true);
        setUnfinished(false);
        const verified = await requestStepUp({action: "review protected items"}).catch(() => false);
        setBusy(false);
        if (verified) onVerified();
        else setUnfinished(true);
    };

    return (
        <section id={"information-request-review-verification"}
                 aria-labelledby={"information-request-review-verification-title"}
                 className={styles.panel}>
            <Text id={"information-request-review-verification-title"}
                  weight={"semibold"}>
                Verify again to review protected items
            </Text>
            <Text id={"information-request-review-verification-copy"}
                  className={styles.mutedText}>
                {hiddenItems(awaiting)}
            </Text>
            {unfinished && (
                <MessageBar id={"information-request-review-verification-error"}
                            intent={"error"}>
                    <MessageBarBody id={"information-request-review-verification-error-body"}>
                        Verification did not finish.
                    </MessageBarBody>
                </MessageBar>
            )}
            <Button id={"information-request-review-verification-btn"}
                    shape={"circular"}
                    appearance={"primary"}
                    className={styles.action}
                    icon={busy
                        ? (
                            <Spinner id={"information-request-review-verification-spinner"}
                                     size={"tiny"}/>
                        )
                        : <ShieldCheckmarkRegular/>}
                    disabled={busy}
                    onClick={() => { void verify(); }}>
                Verify again
            </Button>
        </section>
    );
};

export default ReviewVerificationPanel;
