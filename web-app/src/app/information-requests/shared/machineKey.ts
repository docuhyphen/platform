const MACHINE_KEY = /^[a-z0-9][a-z0-9._-]{0,127}$/;
const LETTER_KEY = /^[a-z][a-z0-9._-]{0,127}$/;

export const MACHINE_KEY_MESSAGE = "Use lowercase letters, digits, dots, dashes, or underscores, starting with a letter or digit.";

export const LETTER_KEY_MESSAGE = "Use lowercase letters, digits, dots, dashes, or underscores, starting with a letter.";

export const isMachineKey = (value: string): boolean => MACHINE_KEY.test(value.trim());

export const machineKeyProblem = (value: string): string | undefined =>
    value.trim() && !isMachineKey(value) ? MACHINE_KEY_MESSAGE : undefined;

export const isLetterKey = (value: string): boolean => LETTER_KEY.test(value.trim());

export const letterKeyProblem = (value: string): string | undefined =>
    value.trim() && !isLetterKey(value) ? LETTER_KEY_MESSAGE : undefined;
