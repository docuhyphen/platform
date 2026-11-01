import {useRef, useState} from 'react';
import {getTokens} from './variableTokens';

const useVariableTokenInput = (value: string, onChange: (value: string) => void) =>
{
    const [pickerOpen, setPickerOpen] = useState(false);
    const [pickerSearch, setPickerSearch] = useState('');
    const inputRef = useRef<HTMLInputElement | HTMLTextAreaElement | null>(null);
    const cursorRef = useRef<number>(value.length);

    const insertToken = (token: string) =>
    {
        const pos = cursorRef.current;
        const newValue = value.slice(0, pos) + `{{${token}}}` + value.slice(pos);
        onChange(newValue);
        setPickerOpen(false);
        setPickerSearch('');
    };

    const removeToken = (start: number, end: number) =>
    {
        onChange(value.slice(0, start) + value.slice(end));
    };

    const handleInputChange = (raw: string) =>
    {
        if (raw.endsWith('{{'))
        {
            setPickerOpen(true);
        }
        onChange(raw);
    };

    const rememberCursor = (selectionStart: number | null) =>
    {
        cursorRef.current = selectionStart ?? value.length;
    };

    return {
        pickerOpen,
        setPickerOpen,
        pickerSearch,
        setPickerSearch,
        inputRef,
        insertToken,
        removeToken,
        handleInputChange,
        rememberCursor,
        tokens: getTokens(value),
    };
};

export default useVariableTokenInput;
