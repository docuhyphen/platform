import {Button, Text} from "@fluentui/react-components";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";
import {useAuditPagerStyles} from "./AuditPagerStyles.tsx";

interface AuditPagerProps {
    idPrefix: string;
    offset: number;
    pageSize: number;
    total: number;
    onOffsetChange: (offset: number) => void;
}

const AuditPager = ({idPrefix, offset, pageSize, total, onOffsetChange}: AuditPagerProps) => {
    const styles = useAuditPagerStyles();
    if (total <= pageSize) return null;
    const last = Math.min(offset + pageSize, total);

    return (
        <div id={`${idPrefix}-pager`}
             className={styles.pager}>
            <Button id={`${idPrefix}-previous`}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={offset === 0}
                    onClick={() => onOffsetChange(Math.max(0, offset - pageSize))}>
                Previous
            </Button>
            <Text id={`${idPrefix}-range`}>
                {`${formatInformationRequestCount(offset + 1)} to ${formatInformationRequestCount(last)} of ${formatInformationRequestCount(total)}`}
            </Text>
            <Button id={`${idPrefix}-next`}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={offset + pageSize >= total}
                    onClick={() => onOffsetChange(offset + pageSize)}>
                Next
            </Button>
        </div>
    );
};

export default AuditPager;
