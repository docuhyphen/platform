import {Button, Text} from "@fluentui/react-components";
import {useExchangeDetailsErrorStyles} from "./ExchangeDetailsErrorStyles.tsx";

const ExchangeDetailsError = ({onRetry}: { onRetry: () => void }) => {
    const styles = useExchangeDetailsErrorStyles();

    return (
        <section id="exchange-details-error"
                 role="alert"
                 className={styles.container}>
            <Text id="exchange-details-error-message">Could not load this Exchange. Please try again.</Text>
            <Button id="exchange-details-error-retry"
                    appearance="primary"
                    shape="circular"
                    onClick={onRetry}>
                Retry
            </Button>
        </section>
    );
};

export default ExchangeDetailsError;
