import React from 'react';
import {Checkbox, InfoLabel, Radio, RadioGroup} from '@fluentui/react-components';
import {useDownloadFormatRestrictionStyles} from "./DownloadFormatRestrictionStyles.tsx";

const SUPPORTED_DOWNLOAD_FORMATS = ['PDF', 'DOCX', 'DOC', 'XLSX', 'XLS', 'PPTX', 'PPT', 'PNG', 'JPG'];

interface DownloadFormatRestrictionProps
{
    allowedDownloadFormats: string[] | undefined;
    onChange: (formats: string[] | undefined) => void;
    disabled?: boolean;
}

const DownloadFormatRestriction: React.FC<DownloadFormatRestrictionProps> = ({allowedDownloadFormats, onChange: onFormatsChange, disabled}) =>
{
    const styles = useDownloadFormatRestrictionStyles();
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
            if (next.length === 0) return;
        }
        onFormatsChange(next);
    };

    return (
        <div id={"download-format-restriction-div-1"}
             className={styles.container}>
            <InfoLabel
                id={"download-format-restriction-info-label-1"}
                size="small"
                weight="semibold"
                info="When restricted, recipients can only download original files in the selected formats. Documents in other formats will still be viewable and available as a PDF download."
            >
                Download format restriction
            </InfoLabel>
            <RadioGroup
                id={"download-format-restriction-radio-group"}
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
                <Radio id={"download-format-restriction-radio-1"}
                       value="all"
                       label="All formats (no restriction)"/>
                <Radio id={"download-format-restriction-radio-2"}
                       value="restrict"
                       label="Restrict to selected formats"/>
            </RadioGroup>
            {formatRestrictionActive && (
                <div id={"download-format-restriction-div-2"}
                     className={styles.formatCheckboxes}>
                    {SUPPORTED_DOWNLOAD_FORMATS.map(format => (
                        <Checkbox
                            id={`download-format-checkbox-${format.toLowerCase()}`}
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
