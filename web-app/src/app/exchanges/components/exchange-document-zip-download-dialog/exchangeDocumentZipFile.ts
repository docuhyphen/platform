const DOWNLOAD_LINK_ID = 'f-download-link';

export const saveZipFile = (data: BlobPart, downloadName: string): void =>
{
    const url = window.URL.createObjectURL(new Blob([data], {type: 'application/octet-stream'}));
    const link = window.document.createElement('a');

    link.id = DOWNLOAD_LINK_ID;
    link.href = url;
    link.setAttribute('download', `${downloadName}.zip`);

    window.document.body.appendChild(link);
    link.click();
    window.document.getElementById(DOWNLOAD_LINK_ID)?.remove();
};
