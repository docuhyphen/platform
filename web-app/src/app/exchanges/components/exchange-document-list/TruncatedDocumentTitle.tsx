import React from "react";
import {Text, Tooltip} from "@fluentui/react-components";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";

interface TruncatedDocumentTitleProps {
    documentId: string;
    title: string;
}

const TruncatedDocumentTitle: React.FC<TruncatedDocumentTitleProps> = ({documentId, title}) => {
    const styles = useExchangeDocumentsListStyles();
    const titleRef = React.useRef<HTMLSpanElement>(null);
    const [isTruncated, setIsTruncated] = React.useState(false);

    React.useLayoutEffect(() => {
        const element = titleRef.current;
        if (!element) return;
        const update = () => setIsTruncated(element.scrollWidth > element.clientWidth);
        update();
        const observer = typeof ResizeObserver === "undefined" ? undefined : new ResizeObserver(update);
        observer?.observe(element);
        return () => observer?.disconnect();
    }, [title]);

    const titleText = (
        <Text id={`exchange-document-title-${documentId}`}
              ref={titleRef}
              className={styles.cardTitle}
              weight="semibold"
              truncate>
            {title}
        </Text>
    );

    return isTruncated ? (
        <Tooltip content={title} relationship="description">
            {titleText}
        </Tooltip>
    ) : titleText;
};

export default TruncatedDocumentTitle;
