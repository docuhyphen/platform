import {listInformationRequestReviews} from "../../../../services/informationRequestReviewService.ts";
import {InformationRequestReviewDto} from "../../../models/models.tsx";
import {informationRequestRefusalMessage, unlessForbidden} from "../../shared/informationRequestRefusal.ts";
import {ServerRead, useServerRead} from "../../shared/useServerRead.ts";

const REVIEWS_FAILURE = "The reviews of this request could not be loaded.";

export const useRequestReviews = (requestId: string): ServerRead<InformationRequestReviewDto[] | null> =>
    useServerRead(
        requestId,
        () => unlessForbidden(listInformationRequestReviews(requestId)),
        refusal => informationRequestRefusalMessage(refusal, REVIEWS_FAILURE),
    );
