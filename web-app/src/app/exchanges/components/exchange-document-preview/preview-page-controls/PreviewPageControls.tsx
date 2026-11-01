import {Button, Input, Text} from "@fluentui/react-components";
import {FirstPageIcon, LastPageIcon, NextPageIcon, PreviousPageIcon} from "../../../../components/IconBundles.tsx";
import {PreviewPageNavigation} from "../usePreviewPageNavigation.ts";
import {usePreviewPageControlsStyles} from "./PreviewPageControlsStyles.tsx";

interface PreviewPageControlsProps
{
    containerId: string;
    pageCountTextId: string;
    navigation: PreviewPageNavigation;
}

const PreviewPageControls = ({containerId, pageCountTextId, navigation}: PreviewPageControlsProps) =>
{
    const styles = usePreviewPageControlsStyles();
    const {numPages, currentPage, pageInput} = navigation;

    const finishEditing = () =>
    {
        navigation.setIsEditingPageInput(false);
        navigation.commitPageInput();
    };

    return (
        <div id={containerId}
             className={styles.pagesInputContainer}>
            <Button onClick={() => navigation.goToPage(1)}
                    id="exchange-document-preview-page-first"
                    icon={<FirstPageIcon/>}
                    disabled={numPages === 0}
                    appearance="transparent"
                    shape={"circular"}/>

            <Button onClick={navigation.goToPreviousPage}
                    id="exchange-document-preview-page-previous"
                    appearance="transparent"
                    shape={"circular"}
                    disabled={numPages === 0 || currentPage <= 1}
                    icon={<PreviousPageIcon/>}/>

            <Input id="exchange-document-preview-page-input"
                   type="text"
                   value={pageInput}
                   onChange={navigation.handlePageInputChange}
                   onFocus={() => navigation.setIsEditingPageInput(true)}
                   onBlur={finishEditing}
                   onKeyDown={(event) =>
                   {
                       if (event.key === 'Enter')
                       {
                           finishEditing();
                       }
                   }}
                   className={styles.pagesInput}
                   contentAfter={<Text id={pageCountTextId}
                                       className={styles.pagesInputAfter}>{` / ${numPages}`}</Text>}
            />

            <Button onClick={navigation.goToNextPage}
                    id="exchange-document-preview-page-next"
                    appearance="transparent"
                    shape={"circular"}
                    disabled={numPages === 0 || currentPage >= numPages}
                    icon={<NextPageIcon/>}/>

            <Button onClick={() => navigation.goToPage(numPages)}
                    id="exchange-document-preview-page-last"
                    icon={<LastPageIcon/>}
                    disabled={numPages === 0}
                    appearance="transparent"
                    shape={"circular"}/>
        </div>
    );
};

export default PreviewPageControls;
