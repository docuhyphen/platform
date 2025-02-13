import React from 'react';
import {Spinner} from "@fluentui/react-components";

const PreLanding: React.FC = () => {
    return (
        <div id={"pre-loading-container"}>
            <Spinner labelPosition="after"
                     size="small"
                     label="Loading Sharing Sessions" />
        </div>
    );
};


export default PreLanding;