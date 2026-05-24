import {jwtDecode} from "jwt-decode";

interface DecodedToken
{
    exp: number;
}

export const isTokenExpired = (token: string): boolean =>
{
    try
    {
        const decoded: DecodedToken = jwtDecode(token);
        const currentTime = Date.now() / 1000;
        return decoded.exp < currentTime;
    }
    catch (error)
    {
        return true;
    }
};

export const getTokenSecondsToExpiry = (token: string): number =>
{
    try
    {
        const decoded: DecodedToken = jwtDecode(token);
        const currentTime = Date.now() / 1000;
        return Math.max(0, decoded.exp - currentTime);
    }
    catch
    {
        return 0;
    }
};

export const isValidEmail = (email: string): boolean =>
{
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
};
