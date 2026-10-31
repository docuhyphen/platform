import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {INFORMATION_REQUEST_RECORD_TYPE} from "../../../../services/recordPreservationService.ts";
import PlaceHoldDialog from "../../../record-preservation/place-hold-dialog/PlaceHoldDialog.tsx";

interface OperationsRowHoldActionProps
{
    idPrefix: string;
    requestId: string;
    title: string;
}

const OperationsRowHoldAction = ({idPrefix, requestId, title}: OperationsRowHoldActionProps) =>
{
    const [placing, setPlacing] = useState(false);
    const [placed, setPlaced] = useState(false);

    return (
        <>
            {placed && (
                <Text id={`${idPrefix}-hold-placed`}
                      role={"status"}>
                    {`Hold placed on ${title}.`}
                </Text>
            )}
            <Button id={`${idPrefix}-place-hold`}
                    appearance={"secondary"}
                    shape={"circular"}
                    aria-label={`Place a hold on ${title}`}
                    onClick={() => setPlacing(true)}>
                Place hold
            </Button>
            {placing && (
                <PlaceHoldDialog resourceType={INFORMATION_REQUEST_RECORD_TYPE}
                                 resourceId={requestId}
                                 onPlaced={() => {
                                     setPlacing(false);
                                     setPlaced(true);
                                 }}
                                 onDismiss={() => setPlacing(false)}/>
            )}
        </>
    );
};

export default OperationsRowHoldAction;
