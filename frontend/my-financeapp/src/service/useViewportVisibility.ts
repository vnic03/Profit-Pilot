import {useEffect, useRef, useState} from "react";

export function useViewportVisibility() {
    const elementRef = useRef<HTMLElement | null>(null);
    const [visible, setVisible] = useState(false);

    useEffect(() => {
        const element = elementRef.current;

        if (!element || typeof IntersectionObserver === "undefined") {
            return;
        }

        const observer = new IntersectionObserver(
            entries => {
                setVisible(entries.some(entry => entry.isIntersecting));
            },
            {
                root: null,
                rootMargin: "0px",
                threshold: 0
            }
        );

        observer.observe(element);

        return () => observer.disconnect();
    }, []);

    return {elementRef, visible};
}
