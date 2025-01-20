import {useAuth} from "./AuthContext.tsx";

const useToken = () =>
{
    const {token} = useAuth();
    return token;
};

export default useToken;