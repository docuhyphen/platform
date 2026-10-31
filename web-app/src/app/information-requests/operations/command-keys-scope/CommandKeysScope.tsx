import React, {useState} from "react";
import {CommandKeysContext} from "../../shared/useStableIdempotencyKey.ts";

interface CommandKeysScopeProps {
    children: React.ReactNode;
}

const CommandKeysScope = ({children}: CommandKeysScopeProps) => {
    const [keys] = useState(() => new Map<string, string>());

    return (
        <CommandKeysContext.Provider value={keys}>
            {children}
        </CommandKeysContext.Provider>
    );
};

export default CommandKeysScope;
