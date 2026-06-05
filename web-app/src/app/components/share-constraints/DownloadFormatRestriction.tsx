import React from 'react';
import {Checkbox, Field, InfoLabel, Radio, RadioGroup, Text} from '@fluentui/react-components';

const SUPPORTED_DOWNLOAD_FORMATS = ['PDF', 'DOCX', 'DOC', 'XLSX', 'XLS', 'PPTX', 'PPT', 'PNG', 'JPG'];

interface DownloadFormatRestrictionProps
{
    allowedDownloadFormats: string[] | undefined;
    onChange: (formats: string[] | undefined) => void;
    disabled?: boolean;
}

/**
 * Download format restriction picker.
 * Renders "All formats" / "Restrict to selected formats" toggles with per-format checkboxes.
 * Should be placed directly below an "Allow document download" switch.
 */
const DownloadFormatRestriction: React.FC<DownloadFormatRestrictionProps> = ({allowedDownloadFormats, onChange: onFormatsChange, disabled}) =>
{
    const formatRestrictionActive = !!allowedDownloadFormats;

    const handleFormatToggle = (format: string, checked: boolean) =>
    {
        const current = allowedDownloadFormats || [];
        let next: string[];
        if (checked)
        {
            next = [...current, format];
        }
        else
        {
            next = current.filter(f => f !== format);
            // At least one format must remain checked
            if (next.length === 0) return;
        }
        onFormatsChange(next);
    };

    return (
        <div style={{display: 'flex', flexDirection: 'column', gap: 4, marginLeft: 24, marginTop: 4}}>
            <InfoLabel
                size="small"
                weight="semibold"
                info="When restricted, recipients can only download original files in the selected formats. Documents in other formats will still be viewable and available as a PDF download."
            >
                Download format restriction
            </InfoLabel>
            <RadioGroup
                value={formatRestrictionActive ? 'restrict' : 'all'}
                disabled={disabled}
                onChange={(_e, data) =>
                {
                    if (data.value === 'all')
                    {
                        onFormatsChange(undefined);
                    }
                    else
                    {
                        onFormatsChange(['PDF']);
                    }
                }}
            >
                <Radio value="all" label="All formats (no restriction)"/>
                <Radio value="restrict" label="Restrict to selected formats"/>
            </RadioGroup>
            {formatRestrictionActive && (
                <div style={{display: 'flex', flexWrap: 'wrap', gap: 4, marginLeft: 24}}>
                    {SUPPORTED_DOWNLOAD_FORMATS.map(format => (
                        <Checkbox
                            key={format}
                            label={format}
                            checked={(allowedDownloadFormats || []).includes(format)}
                            disabled={disabled}
                            onChange={(_e, d) => handleFormatToggle(format, !!d.checked)}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};

export default DownloadFormatRestriction;
