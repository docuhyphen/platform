import {Text} from "@fluentui/react-components";
import {useRespondentLinkNoticeStyles} from "./RespondentLinkNoticeStyles.tsx";

interface Props {
    id: string;
    message: string;
}

const RespondentLinkNotice = ({id, message}: Props) => {
    const styles = useRespondentLinkNoticeStyles();

    return (
        <div id={id}
             className={styles.centered}>
            <Text id={`${id}-text`}
                  className={styles.error}>
                {message}
            </Text>
        </div>
    );
};

export default RespondentLinkNotice;
