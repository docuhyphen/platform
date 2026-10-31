import {Button, Text} from "@fluentui/react-components";
import {focusRequirement} from "../../submission/submissionReview.ts";
import {RefusedResponseItem} from "../responseRefusalItems.ts";
import {useStructuredResponseRefusalItemsStyles} from "./StructuredResponseRefusalItemsStyles.tsx";

interface Props {
    items: RefusedResponseItem[];
}

const StructuredResponseRefusalItems = ({items}: Props) => {
    const styles = useStructuredResponseRefusalItemsStyles();
    if (items.length === 0) return null;

    return (
        <ul id={"information-request-response-refused-items"}
            className={styles.list}>
            {items.map((item, index) => {
                const id = `information-request-response-refused-item-${index}`;
                return (
                    <li id={id}
                        key={`${item.requirementId}-${item.detail}`}
                        className={styles.item}>
                        <Button id={`${id}-go`}
                                appearance={"transparent"}
                                shape={"circular"}
                                size={"small"}
                                aria-label={`Go to ${item.label}`}
                                onClick={() => focusRequirement(item.occurrencePath, item.requirementKey)}>
                            {item.label}
                        </Button>
                        {item.detail && (
                            <Text id={`${id}-detail`}
                                  className={styles.detail}>
                                {item.detail}
                            </Text>
                        )}
                    </li>
                );
            })}
        </ul>
    );
};

export default StructuredResponseRefusalItems;
