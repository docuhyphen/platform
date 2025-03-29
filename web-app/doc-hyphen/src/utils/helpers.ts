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
        console.log("Token expiration time:", new Date(decoded.exp * 1000).toLocaleString());

        return decoded.exp < currentTime;
    }
    catch (error)
    {
        return true;
    }
};

export const isValidEmail = (email: string): boolean =>
{
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
};
