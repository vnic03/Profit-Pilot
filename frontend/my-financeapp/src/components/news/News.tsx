import React, {useEffect, useState} from "react";
import {getNews} from "../../service/newsService";

interface NewsArticle {
    title: string;
    publicationDate: string;
    description: string;
    url: string;
    imageUrl: string | null;
}

interface NewsResponse {
    status: "disabled" | "ready";
    articles: NewsArticle[];
}

type NewsState =
    | {status: "loading"}
    | {status: "disabled"}
    | {status: "error"}
    | {status: "ready"; articles: NewsArticle[]};

const News: React.FC = () => {
    const [state, setState] = useState<NewsState>({status: "loading"});
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        let cancelled = false;

        setState({status: "loading"});

        const loadNews = async () => {
            try {
                const response: NewsResponse = await getNews("Apple");

                if (cancelled) {
                    return;
                }

                if (response.status === "disabled") {
                    setState({status: "disabled"});
                } else if (
                    response.status === "ready"
                    && Array.isArray(response.articles)
                ) {
                    setState({
                        status: "ready",
                        articles: response.articles
                    });
                } else {
                    setState({status: "error"});
                }
            } catch {
                if (!cancelled) {
                    setState({status: "error"});
                }
            }
        };

        void loadNews();

        return () => {
            cancelled = true;
        };
    }, [attempt]);

    return (
        <section aria-label="Company news">
            <h2>News: Apple</h2>

            {state.status === "loading" && (
                <p role="status">Loading news...</p>
            )}

            {state.status === "disabled" && (
                <p role="status">
                    News is currently disabled.
                </p>
            )}

            {state.status === "error" && (
                <div role="alert">
                    <p>Could not load news. Please try again later.</p>
                    <button
                        onClick={() => {
                            setState({status: "loading"});
                            setAttempt(value => value + 1);
                        }}
                    >
                        Retry
                    </button>
                </div>
            )}

            {state.status === "ready" && (
                state.articles.length === 0 ? (
                    <p>No news found for Apple.</p>
                ) : (
                    <ul>
                        {state.articles.map((article, index) => {
                            const published = new Date(article.publicationDate);
                            const validDate = !Number.isNaN(published.getTime());

                            return (
                                <li key={`${article.url}-${index}`}>
                                    <a
                                        href={article.url}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                    >
                                        <h3>{article.title}</h3>
                                    </a>

                                    {article.imageUrl && (
                                        <img
                                            src={article.imageUrl}
                                            alt=""
                                            loading="lazy"
                                            style={{width: "100px", height: "auto"}}
                                        />
                                    )}

                                    {validDate && (
                                        <time dateTime={published.toISOString()}>
                                            {published.toLocaleDateString()}
                                        </time>
                                    )}

                                    <p>{article.description}</p>
                                </li>
                            );
                        })}
                    </ul>
                )
            )}
        </section>
    );
};

export default News;
