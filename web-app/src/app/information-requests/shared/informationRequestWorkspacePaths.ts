export const informationRequestManagePath = (requestId: string): string => `/information-requests/${requestId}/manage`;

export const informationRequestRespondPath = (requestId: string): string => `/information-requests/${requestId}/respond`;

export const informationRequestReviewPath = (requestId: string, reviewId: string): string =>
    `/information-requests/${requestId}/reviews/${reviewId}`;

export const INFORMATION_REQUEST_REVIEW_QUEUE_PATH = "/information-request-reviews";
