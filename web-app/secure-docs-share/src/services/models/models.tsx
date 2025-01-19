export interface ResponseError
{
    errorMessage?: string
}

export interface SignUpInitiationRequest
{
    email: string
}

export interface SignUpCompletionRequest
{
    email: string,
    otp: string,
    password: string,
    confirmationPassword: string
}

export interface SignUpOtpRegenerationRequest
{
    email: string
}

export interface SignInInitiationRequest
{
    email: string,
    password: string,
}

export interface SignInCompletionRequest
{
    email: string
    otp: string
}