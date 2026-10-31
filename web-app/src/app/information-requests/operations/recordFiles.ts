export const saveJsonFile = (fileName: string, content: unknown) => {
    const url = window.URL.createObjectURL(new Blob([JSON.stringify(content, null, 2)], {type: "application/json"}));
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    link.click();
    window.URL.revokeObjectURL(url);
};
