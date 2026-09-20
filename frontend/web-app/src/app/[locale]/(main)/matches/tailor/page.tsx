import {
    CvTailoringWorkspace
} from "@/components/matching/cv-tailoring-workspace";

type CvTailoringPageProps = {
    searchParams: Promise<{
        jobId?: string | string[];
    }>;
};

export default async function CvTailoringPage({
                                                  searchParams
                                              }: CvTailoringPageProps) {
    const params =
        await searchParams;

    const rawJobId =
        params.jobId;

    const jobId =
        Array.isArray(
            rawJobId
        )
            ? rawJobId[0] ??
            null
            : rawJobId ??
            null;

    return (
        <CvTailoringWorkspace
            jobId={
                jobId
            }
        />
    );
}