export const DEFAULT_HELP_PANEL_WIDTH = 400;
const MIN_PANEL_WIDTH = 320;
const WINDOW_PADDING = 80;

export function clampHelpPanelWidth(width: number): number
{
    const maxWidth = Math.max(MIN_PANEL_WIDTH, window.innerWidth - WINDOW_PADDING);
    return Math.min(Math.max(width, MIN_PANEL_WIDTH), maxWidth);
}
