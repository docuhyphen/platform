import {
    Button,
    Input,
    Text,
} from "@fluentui/react-components";
import {
    ArrowNextFilled,
    ArrowNextRegular,
    ArrowPreviousFilled,
    ArrowPreviousRegular,
    ChevronLeftFilled,
    ChevronLeftRegular,
    ChevronRightFilled,
    ChevronRightRegular,
    bundleIcon,
} from "@fluentui/react-icons";
import {useIndustryDocumentToolbarStyles} from "./IndustryDocumentToolbarStyles.tsx";

const FirstPageIcon = bundleIcon(ArrowPreviousFilled, ArrowPreviousRegular);
const LastPageIcon = bundleIcon(ArrowNextFilled, ArrowNextRegular);
const PreviousPageIcon = bundleIcon(ChevronLeftFilled, ChevronLeftRegular);
const NextPageIcon = bundleIcon(ChevronRightFilled, ChevronRightRegular);

export function IndustryDocumentPageControls()
{
    const styles = useIndustryDocumentToolbarStyles();

    return (
        <div
            id="industry-demo-document-preview-pages"
            className={styles.pagesInputContainer}
        >
            <Button
                id="industry-demo-document-preview-page-first"
                appearance="transparent"
                shape="circular"
                aria-label="First page"
                icon={<FirstPageIcon id="industry-demo-document-preview-page-first-icon"/>}
            />

            <Button
                id="industry-demo-document-preview-page-previous"
                appearance="transparent"
                shape="circular"
                disabled={true}
                aria-label="Previous page"
                icon={<PreviousPageIcon id="industry-demo-document-preview-page-previous-icon"/>}
            />

            <Input
                id="industry-demo-document-preview-page-input"
                className={styles.pagesInput}
                type="text"
                value="1"
                readOnly
                aria-label="Current page"
                contentAfter={(
                    <Text
                        id="industry-demo-document-preview-page-total"
                        className={styles.pagesInputAfter}
                    >
                        / 1
                    </Text>
                )}
            />

            <Button
                id="industry-demo-document-preview-page-next"
                appearance="transparent"
                shape="circular"
                disabled={true}
                aria-label="Next page"
                icon={<NextPageIcon id="industry-demo-document-preview-page-next-icon"/>}
            />

            <Button
                id="industry-demo-document-preview-page-last"
                appearance="transparent"
                shape="circular"
                aria-label="Last page"
                icon={<LastPageIcon id="industry-demo-document-preview-page-last-icon"/>}
            />
        </div>
    );
}
