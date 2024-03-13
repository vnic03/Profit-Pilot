import React, { useEffect, useState } from 'react';
import {getNews} from "../../service/newsService";

interface NewsArticle {
    title: string;
    publicationDate: string;
    description: string;
    url: string;
    imageUrl: string;
}

const News: React.FC = () => {
    const [articles, setArticles] = useState<NewsArticle[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string>('');

    useEffect(() => {
        const fetchNews = async () => {
            try {
                const data = await getNews('Apple');
                setArticles(data);

            } catch (err) {
                console.error(err);
                setError('Error while fetching news. . .');
            } finally {
                setLoading(false);
            }
        }
        fetchNews();

    }, []);

    if (loading) return <p>Loading...</p>;
    if (error) return <p>{error}</p>;

    return (
        <div>
            <h2>News</h2>
            <ul>
                {articles.map((article, index) => (
                    <li key={index}>
                        <a href={article.url} target="_blank" rel="noopener noreferrer">
                            <h3>{article.title}</h3>
                        </a>
                        {article.imageUrl &&
                            <img src={article.imageUrl} alt={article.title} style={{width: '100px', height: 'auto'}}/>}
                        <p>{new Date(article.publicationDate).toLocaleDateString()} - {article.description}</p>
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default News;